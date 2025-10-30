//cd "C:\Users\arhas\Downloads\VirtualICTrainer"
//javac --module-path "C:\Users\arhas\Downloads\openjfx-24.0.2_windows-x64_bin-sdk\javafx-sdk-24.0.2\lib" --add-modules javafx.controls,javafx.fxml VirtualICTrainer.java
//java --module-path "C:\Users\arhas\Downloads\openjfx-24.0.2_windows-x64_bin-sdk\javafx-sdk-24.0.2\lib" --add-modules javafx.controls,javafx.fxml VirtualICTrainer

import javafx.application.Application;
import javafx.application.Platform;
import javafx.geometry.*;
import javafx.scene.*;
import javafx.scene.control.*;
import javafx.scene.effect.DropShadow;
import javafx.scene.input.*;
import javafx.scene.layout.*;
import javafx.scene.paint.*;
import javafx.scene.shape.Circle;
import javafx.scene.shape.LineTo;
import javafx.scene.shape.MoveTo;
import javafx.scene.shape.Path;
import javafx.scene.shape.Rectangle;
import javafx.scene.shape.StrokeLineCap;
import javafx.animation.*;

import javafx.util.Duration;

import javafx.scene.text.*;
import javafx.stage.*;
import java.io.*;
import java.util.*;
import java.util.stream.Collectors;

public class VirtualICTrainer extends Application {

    private Pane board;
    private ToggleButton powerToggle;
    private final List<ExternalSwitch> externalSwitches = new ArrayList<>();
    private final List<ExternalLED> externalLEDs = new ArrayList<>();
    private final List<ICBase> ics = new ArrayList<>();
    private final List<Wire> wires = new ArrayList<>();
    private Pin connectionStart = null;
    private PowerNode vccNode = null;
private PowerNode gndNode = null;

    
    private final Deque<ExternalLED> undoStack = new ArrayDeque<>();

    private final Color[] palette = {
        Color.web("#00ffff"), Color.web("#ff6b6b"),
        Color.web("#2ecc71"), Color.web("#f1c40f"),
        Color.web("#9b59b6"), Color.web("#e67e22")
    };
    private int paletteIndex = 0;

    @Override
    public void start(Stage stage) {
        board = new Pane();
        board.setPrefSize(1400, 820);
        board.setStyle("-fx-background-color: linear-gradient(to bottom right,#1e272e,#2f3640);");

        ToolBar toolbar = new ToolBar();
        toolbar.setStyle("-fx-background-color:linear-gradient(#3a3a3a,#222);-fx-border-color:#555;-fx-border-radius:6;");
        toolbar.setLayoutX(12);
        toolbar.setLayoutY(12);
        toolbar.setPadding(new Insets(5,8,5,8));
        toolbar.setEffect(new DropShadow(8,Color.color(0,0,0,0.5)));

        powerToggle = new ToggleButton("POWER OFF");
        stylePowerButton();
        powerToggle.setOnAction(e -> { stylePowerButton(); evaluateAll(); });
        Tooltip.install(powerToggle,new Tooltip("Toggle Power"));

        Button b7400=new Button("7400 NAND"),b7402=new Button("7402 NOR"),
               b7408=new Button("7408 AND"),b7432=new Button("7432 OR"),
               b7404=new Button("7404 NOT"),b7486=new Button("7486 XOR"),
               b7487=new Button("7487 XNOR");
        Button truthBtn=new Button("Truth Table");
        Button undoLED=new Button("Undo LED");
        Button clearW=new Button("Clear Wires");
        Button saveBtn=new Button("Save");
        Button loadBtn=new Button("Load");

        b7400.setOnAction(e->placeIC(new IC7400(200,120)));
        b7402.setOnAction(e->placeIC(new IC7402(420,120)));
        b7408.setOnAction(e->placeIC(new IC7408(200,300)));
        b7432.setOnAction(e->placeIC(new IC7432(420,300)));
        b7404.setOnAction(e->placeIC(new IC7404(640,120)));
        b7486.setOnAction(e->placeIC(new IC7486(640,300)));
        b7487.setOnAction(e->placeIC(new IC7487(860,200)));
        truthBtn.setOnAction(e->showTruthTable());
        undoLED.setOnAction(e->undoLEDRemoval());
        clearW.setOnAction(e->{for(Wire w:new ArrayList<>(wires))w.remove();wires.clear();evaluateAll();});
        saveBtn.setOnAction(e->saveCircuit(stage));
        loadBtn.setOnAction(e->loadCircuit(stage));

        toolbar.getItems().addAll(powerToggle,b7400,b7402,b7408,b7432,b7404,b7486,b7487,
                new Separator(),truthBtn,new Separator(),undoLED,new Separator(),clearW,saveBtn,loadBtn);
        board.getChildren().add(toolbar);

        for(int i=0;i<8;i++){
            ExternalSwitch s=new ExternalSwitch(40,100+i*60,i+1);
            externalSwitches.add(s);
            board.getChildren().add(s.group);
            makeDraggable(s.group);
        }

        // Outputs
        for(int i=0;i<8;i++){
            ExternalLED l=new ExternalLED(1220,120+i*60,i+1);
            externalLEDs.add(l);
            board.getChildren().add(l.group);
        }

        Scene scene=new Scene(board);
        stage.setResizable(false);
scene.setFill(Color.web("#1e272e"));
stage.getIcons().add(new javafx.scene.image.Image("https://cdn-icons-png.flaticon.com/512/1483/1483336.png"));

        scene.setOnKeyPressed(k->{
            if(k.getCode()==KeyCode.DELETE&&selectedWire!=null){
                selectedWire.remove();wires.remove(selectedWire);selectedWire=null;evaluateAll();
            }
        });
        stage.setScene(scene);
        stage.setTitle("Virtual IC Trainer – Dark PCB Edition");
        stage.show();
    }

    private Wire selectedWire;
    // ---------- PIN & WIRE SYSTEM ----------
    private enum PinType { INPUT, OUTPUT, POWER, GROUND }

    private class Pin {
        ICBase owner; int number=-1; PinType type; boolean value=false;
        Circle visual; List<Wire> connections=new ArrayList<>();
        Pin(ICBase owner,PinType type){this.owner=owner;this.type=type;}
        void setValue(boolean v){
            if(this.value==v)return;
            this.value=v;
            if(type==PinType.OUTPUT||type==PinType.POWER||type==PinType.GROUND){
                for(Wire w:new ArrayList<>(connections)){
                    Pin other=w.other(this);
                    if(other.type==PinType.INPUT){
                        other.setValue(v);
                        if(other.owner!=null)other.owner.updateLogic();
                    }
                }
            }
            Platform.runLater(()->externalLEDs.forEach(ExternalLED::refresh));
        }
        void setHighlighted(boolean on){
            if(visual!=null){
                visual.setStroke(on?Color.YELLOW:Color.BLACK);
                visual.setStrokeWidth(on?2.5:1);
            }
        }
    }

    private class Wire {
        Pin a,b; Path path; Color color; boolean selected=false;
        Wire(Pin from,Pin to){
            this.a=from;this.b=to;
            a.connections.add(this); b.connections.add(this);
            color=palette[(paletteIndex++)%palette.length];
            path=new Path();
            path.setStroke(color); path.setStrokeWidth(4);
            path.setStrokeLineCap(StrokeLineCap.ROUND);
            path.setFill(Color.TRANSPARENT);
            path.setEffect(new DropShadow(6,Color.color(0,0,0,0.35)));
            redraw();
            board.getChildren().add(0,path);

           path.setOnMouseClicked(e -> {
    if (e.getButton() == MouseButton.PRIMARY) {
        // Normal selection
        if (connectionStart == null) {
            if (selectedWire != null && selectedWire != this) selectedWire.setSelected(false);
            setSelected(!selected);
            selectedWire = selected ? this : null;
        } else {
            // If user was connecting and clicked a wire, "club" this wire with the selected pin
            connectionStart.setHighlighted(false);
            clubWireWithPin(this, connectionStart);
            connectionStart = null;
        }
        e.consume();
    } else if (e.getButton() == MouseButton.SECONDARY) {
        ContextMenu cm = new ContextMenu();
        MenuItem del = new MenuItem("Delete Wire");
        del.setOnAction(ev -> { remove(); wires.remove(this); evaluateAll(); });
        cm.getItems().add(del);
        cm.show(board, e.getScreenX(), e.getScreenY());
    }
});

            // propagate initial value
            if(a.type==PinType.POWER)b.setValue(true);
            else if(a.type==PinType.GROUND)b.setValue(false);
            else if(a.type==PinType.OUTPUT)b.setValue(a.value);
        }
void redraw() {
    path.getElements().clear();
    Point2D pa = pinSceneCenter(a), pb = pinSceneCenter(b);
    Point2D pA = board.sceneToLocal(pa), pB = board.sceneToLocal(pb);

    double controlOffset = Math.abs(pB.getX() - pA.getX()) * 0.5;

    // Smooth cubic Bezier curve between pins
    path.getElements().add(new MoveTo(pA.getX(), pA.getY()));
    path.getElements().add(new javafx.scene.shape.CubicCurveTo(
        pA.getX() + controlOffset, pA.getY(),
        pB.getX() - controlOffset, pB.getY(),
        pB.getX(), pB.getY()
    ));

    path.setStrokeWidth(selected ? 5 : 3.5);
    path.setStroke(color);
    path.setStrokeLineCap(StrokeLineCap.ROUND);
    path.setFill(Color.TRANSPARENT);
    path.setEffect(new DropShadow(8, color));
}

void setSelected(boolean s) {
    selected = s;
    path.setStroke(s ? Color.YELLOW : color);
    path.setStrokeWidth(s ? 5 : 3.5);
    if (s) path.toFront(); else path.toBack();
}

        void remove(){
            a.connections.remove(this); b.connections.remove(this);
            board.getChildren().remove(path);
            resetDisconnectedLEDs();
        }
        Pin other(Pin p){return p==a?b:a;}
    }

    // resets any LED that lost all connections
    private void resetDisconnectedLEDs(){
        for(ExternalLED led:externalLEDs){
            if(led.pin.connections.isEmpty()){
                led.pin.value=false; led.refresh();
            }
        }
    }

    // ---- External Nodes (Switches, LEDs, Power) ----
    
// ---------- POWER NODE PLACEHOLDER ----------
private class PowerNode {
    Pin pin = new Pin(null, PinType.POWER);
    Circle circle = new Circle(0);
    Group group = new Group();
}

    private class ExternalSwitch{
        Group group=new Group(); Circle node; ToggleButton tb; Pin pin;
        ExternalSwitch(double x,double y,int id){
            node=new Circle(12,Color.DARKRED); node.setStroke(Color.BLACK);
            tb=new ToggleButton("0"); tb.setLayoutX(30); tb.setLayoutY(-10);
            tb.setOnAction(e->{boolean v=tb.isSelected(); tb.setText(v?"1":"0");
                node.setFill(v?Color.LIMEGREEN:Color.DARKRED);
                pin.setValue(v); evaluateAll();});
            pin=new Pin(null,PinType.OUTPUT);
            node.setOnMouseClicked(ev->{if(ev.getButton()==MouseButton.PRIMARY)startConnection(pin);});
            Text lbl=new Text("IN"+id); lbl.setFill(Color.WHITE);
            lbl.setLayoutX(-6); lbl.setLayoutY(28);
            group.getChildren().addAll(node,tb,lbl);
            group.setLayoutX(x); group.setLayoutY(y);
            group.setOnContextMenuRequested(ev->{
                ContextMenu cm=new ContextMenu();
                MenuItem reset=new MenuItem("Reset Switch");
                reset.setOnAction(a->{tb.setSelected(false);tb.setText("0");
                    node.setFill(Color.DARKRED);pin.setValue(false);evaluateAll();});
                cm.getItems().add(reset); cm.show(group,ev.getScreenX(),ev.getScreenY());
            });
        }
    }

    private class ExternalLED{
        Group group=new Group(); Rectangle display; Circle node; Pin pin;
        ExternalLED(double x,double y,int id){
            display=new Rectangle(20,20,Color.DARKRED);
            display.setStroke(Color.BLACK); display.setArcWidth(5); display.setArcHeight(5);
            node=new Circle(10,Color.DARKGRAY); node.setStroke(Color.BLACK);
            node.setCenterX(-30); node.setCenterY(10);
            pin=new Pin(null,PinType.INPUT);
            node.setOnMouseClicked(ev->{if(ev.getButton()==MouseButton.PRIMARY)startConnection(pin);});
            Text lbl=new Text("OUT"+id); lbl.setFill(Color.WHITE);
            lbl.setLayoutX(26); lbl.setLayoutY(14);
            group.getChildren().addAll(display,node,lbl);
            group.setLayoutX(x); group.setLayoutY(y);
            group.setOnContextMenuRequested(ev->{
                ContextMenu cm=new ContextMenu();
                MenuItem remove=new MenuItem("Remove LED");
                remove.setOnAction(a->{
                    undoStack.push(this);
                    board.getChildren().remove(group);
                    externalLEDs.remove(this);
                    for(Wire w:new ArrayList<>(wires)){
                        if(w.a==pin||w.b==pin){w.remove();wires.remove(w);}
                    }
                    evaluateAll();
                });
                cm.getItems().add(remove); cm.show(group,ev.getScreenX(),ev.getScreenY());
            });
        }
        void refresh(){
            display.setFill((powerToggle.isSelected()&&pin.value)?Color.LIMEGREEN:Color.DARKRED);
        }
    }

    private void undoLEDRemoval(){
        if(undoStack.isEmpty())return;
        ExternalLED led=undoStack.pop();
        externalLEDs.add(led);
        board.getChildren().add(led.group);
        evaluateAll();
    }
        // ---------- Toolbar Helpers ----------
 // ---------- Toolbar Helpers ----------
private Timeline powerGlowAnim;

private void stylePowerButton() {
    if (powerGlowAnim != null) powerGlowAnim.stop();

    if (powerToggle.isSelected()) {
        // Power ON visuals
        powerToggle.setStyle("-fx-background-color: linear-gradient(#27ae60,#1e8449); -fx-text-fill: white; -fx-font-weight: bold;");

        // Add glowing drop shadow effect
        DropShadow glow = new DropShadow(25, Color.LIMEGREEN);
        glow.setSpread(0.5);
        powerToggle.setEffect(glow);

        // Create pulsing animation for glow radius
        powerGlowAnim = new Timeline(
            new KeyFrame(Duration.ZERO, new KeyValue(glow.radiusProperty(), 15)),
            new KeyFrame(Duration.seconds(1.2), new KeyValue(glow.radiusProperty(), 30))
        );
        powerGlowAnim.setAutoReverse(true);
        powerGlowAnim.setCycleCount(Animation.INDEFINITE);
        powerGlowAnim.play();

        powerToggle.setText("POWER ON");
    } else {
        // Power OFF visuals
        powerToggle.setStyle("-fx-background-color: linear-gradient(#e74c3c,#c0392b); -fx-text-fill: white; -fx-font-weight: bold;");
        powerToggle.setEffect(null);
        powerToggle.setText("POWER OFF");
    }
}


    private void placeIC(ICBase ic) {
        ics.add(ic);
        board.getChildren().add(ic.group);
        makeDraggable(ic.group);
       for (ExternalLED led : externalLEDs) {
    makeDraggable(led.group);
}



        evaluateAll();
    }

    // ---------- IC BASE ----------
private abstract class ICBase {
    Group group = new Group();
    Rectangle body;
    Text title;
    Map<Integer, Pin> pins = new HashMap<>();
    Map<Integer, Circle> pinNodes = new HashMap<>();

    ICBase(String name, double x, double y) {
        body = new Rectangle(160, 140, Color.web("#e0e0e0"));
        body.setStroke(Color.web("#222"));
        body.setArcWidth(10);
        body.setArcHeight(10);

        title = new Text(name);
        title.setFont(Font.font("Roboto", 13));
        title.setFill(Color.BLACK);
        title.setX(10);
        title.setY(18);

        group.getChildren().addAll(body, title);
        group.setLayoutX(x);
        group.setLayoutY(y);

        double leftX = -8, rightX = body.getWidth() + 8, top = 28, gap = 16;
// LEFT SIDE PINS (1–7)
for (int i = 1; i <= 7; i++) {
    double py = top + (i - 1) * gap;
    Circle c = new Circle(leftX, py, 6, Color.web("#222"));
    c.setStroke(Color.WHITE);
    group.getChildren().add(c);


    // --- Pin number label ---
    Text pinNum = new Text(String.valueOf(i));
    pinNum.setFont(Font.font("Consolas", 10));
    pinNum.setFill(Color.BLACK);
    pinNum.setLayoutX(leftX - 22); // position to the left
    pinNum.setLayoutY(py + 4);
    group.getChildren().add(pinNum);

    Pin p = new Pin(this, PinType.INPUT);
    p.number = i;
    p.visual = c;
    pins.put(i, p);
    pinNodes.put(i, c);

    // --- GND (pin 7) special handling ---
    if (i == 7) {
        c.setFill(Color.DARKRED);
        c.setStroke(Color.GRAY);
        c.setOnMouseClicked(null);

        Text gndLbl = new Text("GND");
        gndLbl.setFont(Font.font("Consolas", 9));
        gndLbl.setFill(Color.WHITE);
        gndLbl.setLayoutX(leftX - 45); // more left to avoid overlap
        gndLbl.setLayoutY(py -2);     // slightly above
        group.getChildren().add(gndLbl);
        continue;
    }

    // --- Click to connect ---
    c.setOnMouseClicked(ev -> {
        if (ev.getButton() == MouseButton.PRIMARY) startConnection(p);
    });
}
// RIGHT SIDE PINS (8–14)
for (int j = 14; j >= 8; j--) {
    int idx = 14 - j + 1;
    double py = top + (idx - 1) * gap;
    Circle c = new Circle(rightX, py, 6, Color.web("#222"));
    c.setStroke(Color.WHITE);
    group.getChildren().add(c);

    // --- Pin number label ---
    Text pinNum = new Text(String.valueOf(j));
    pinNum.setFont(Font.font("Consolas", 10));
    pinNum.setFill(Color.BLACK);
    pinNum.setLayoutX(rightX + 10); // to the right of pin
    pinNum.setLayoutY(py + 4);
    group.getChildren().add(pinNum);

    Pin p = new Pin(this, PinType.INPUT);
    p.number = j;
    p.visual = c;
    pins.put(j, p);
    pinNodes.put(j, c);

    // --- VCC (pin 14) special handling ---
    if (j == 14) {
        c.setFill(Color.DARKRED);
        c.setStroke(Color.GRAY);
        c.setOnMouseClicked(null);

        Text vccLbl = new Text("VCC");
        vccLbl.setFont(Font.font("Consolas", 9));
        vccLbl.setFill(Color.WHITE);
        vccLbl.setLayoutX(rightX + 18); // further right to avoid overlap
        vccLbl.setLayoutY(py - 2);      // slightly above pin
        group.getChildren().add(vccLbl);
        continue;
    }

    // --- Click to connect ---
    c.setOnMouseClicked(ev -> {
        if (ev.getButton() == MouseButton.PRIMARY) startConnection(p);
    });
}

        // Right-click → remove IC
        group.setOnContextMenuRequested(ev -> {
            ContextMenu cm = new ContextMenu();
            MenuItem remove = new MenuItem("Remove IC");
            remove.setOnAction(a -> {
                for (Wire w : new ArrayList<>(wires)) {
                    if (w.a.owner == this || w.b.owner == this) {
                        w.remove();
                        wires.remove(w);
                    }
                }
                board.getChildren().remove(group);
                ics.remove(this);
                evaluateAll();
            });
            cm.getItems().add(remove);
            cm.show(group, ev.getScreenX(), ev.getScreenY());
        });
    }

    // Set pin type and color
    void setPinType(int pinNumber, PinType type) {
        Pin p = pins.get(pinNumber);
        if (p == null) return;
        p.type = type;
        Circle c = pinNodes.get(pinNumber);
        if (c != null) {
            switch (type) {
                case INPUT -> c.setFill(Color.web("#444"));
                case OUTPUT -> c.setFill(Color.web("#00aaff"));
                case POWER -> c.setFill(Color.RED);
                case GROUND -> c.setFill(Color.BLACK);
            }
        }
    }

    abstract void updateLogic();

    boolean isPowered() {
        return powerToggle.isSelected(); // All ICs powered when Power ON
    }
}


    // ---------- IC IMPLEMENTATIONS ----------
    private class IC7400 extends ICBase {
        IC7400(double x, double y) {
            super("7400 NAND", x, y);
            setPinType(7, PinType.GROUND); setPinType(14, PinType.POWER);
            setPinType(1, PinType.INPUT); setPinType(2, PinType.INPUT); setPinType(3, PinType.OUTPUT);
            setPinType(4, PinType.INPUT); setPinType(5, PinType.INPUT); setPinType(6, PinType.OUTPUT);
            setPinType(9, PinType.INPUT); setPinType(10, PinType.INPUT); setPinType(8, PinType.OUTPUT);
            setPinType(12, PinType.INPUT); setPinType(13, PinType.INPUT); setPinType(11, PinType.OUTPUT);
        }
        void updateLogic() {
            if (!isPowered()) { for (int p : new int[]{3, 6, 8, 11}) pins.get(p).setValue(false); return; }
            pins.get(3).setValue(!(pins.get(1).value && pins.get(2).value));
            pins.get(6).setValue(!(pins.get(4).value && pins.get(5).value));
            pins.get(8).setValue(!(pins.get(9).value && pins.get(10).value));
            pins.get(11).setValue(!(pins.get(12).value && pins.get(13).value));
        }
    }

    private class IC7402 extends ICBase {
        IC7402(double x, double y) {
            super("7402 NOR", x, y);
            setPinType(7, PinType.GROUND); setPinType(14, PinType.POWER);
            setPinType(1, PinType.OUTPUT); setPinType(2, PinType.INPUT); setPinType(3, PinType.INPUT);
            setPinType(4, PinType.OUTPUT); setPinType(5, PinType.INPUT); setPinType(6, PinType.INPUT);
            setPinType(8, PinType.INPUT); setPinType(9, PinType.INPUT); setPinType(10, PinType.OUTPUT);
            setPinType(11, PinType.INPUT); setPinType(12, PinType.INPUT); setPinType(13, PinType.OUTPUT);
        }
        void updateLogic() {
            if (!isPowered()) { for (int p : new int[]{1, 4, 10, 13}) pins.get(p).setValue(false); return; }
            pins.get(1).setValue(!(pins.get(2).value || pins.get(3).value));
            pins.get(4).setValue(!(pins.get(5).value || pins.get(6).value));
            pins.get(10).setValue(!(pins.get(8).value || pins.get(9).value));
            pins.get(13).setValue(!(pins.get(11).value || pins.get(12).value));
        }
    }

    private class IC7408 extends ICBase {
        IC7408(double x, double y) {
            super("7408 AND", x, y);
            setPinType(7, PinType.GROUND); setPinType(14, PinType.POWER);
            setPinType(1, PinType.INPUT); setPinType(2, PinType.INPUT); setPinType(3, PinType.OUTPUT);
            setPinType(4, PinType.INPUT); setPinType(5, PinType.INPUT); setPinType(6, PinType.OUTPUT);
            setPinType(9, PinType.INPUT); setPinType(10, PinType.INPUT); setPinType(8, PinType.OUTPUT);
            setPinType(12, PinType.INPUT); setPinType(13, PinType.INPUT); setPinType(11, PinType.OUTPUT);
        }
        void updateLogic() {
            if (!isPowered()) { for (int p : new int[]{3, 6, 8, 11}) pins.get(p).setValue(false); return; }
            pins.get(3).setValue(pins.get(1).value && pins.get(2).value);
            pins.get(6).setValue(pins.get(4).value && pins.get(5).value);
            pins.get(8).setValue(pins.get(9).value && pins.get(10).value);
            pins.get(11).setValue(pins.get(12).value && pins.get(13).value);
        }
    }

    private class IC7432 extends ICBase {
        IC7432(double x, double y) {
            super("7432 OR", x, y);
            setPinType(7, PinType.GROUND); setPinType(14, PinType.POWER);
            setPinType(1, PinType.INPUT); setPinType(2, PinType.INPUT); setPinType(3, PinType.OUTPUT);
            setPinType(4, PinType.INPUT); setPinType(5, PinType.INPUT); setPinType(6, PinType.OUTPUT);
            setPinType(9, PinType.INPUT); setPinType(10, PinType.INPUT); setPinType(8, PinType.OUTPUT);
            setPinType(12, PinType.INPUT); setPinType(13, PinType.INPUT); setPinType(11, PinType.OUTPUT);
        }
        void updateLogic() {
            if (!isPowered()) { for (int p : new int[]{3, 6, 8, 11}) pins.get(p).setValue(false); return; }
            pins.get(3).setValue(pins.get(1).value || pins.get(2).value);
            pins.get(6).setValue(pins.get(4).value || pins.get(5).value);
            pins.get(8).setValue(pins.get(9).value || pins.get(10).value);
            pins.get(11).setValue(pins.get(12).value || pins.get(13).value);
        }
    }

    private class IC7404 extends ICBase {
        IC7404(double x, double y) {
            super("7404 NOT", x, y);
            setPinType(7, PinType.GROUND); setPinType(14, PinType.POWER);
            int[][] map = {{1, 2}, {3, 4}, {5, 6}, {8, 9}, {10, 11}, {12, 13}};
            for (int[] m : map) { setPinType(m[0], PinType.INPUT); setPinType(m[1], PinType.OUTPUT); }
        }
        void updateLogic() {
            if (!isPowered()) { for (int[] p : new int[][]{{2}, {4}, {6}, {9}, {11}, {13}}) pins.get(p[0]).setValue(false); return; }
            pins.get(2).setValue(!pins.get(1).value);
            pins.get(4).setValue(!pins.get(3).value);
            pins.get(6).setValue(!pins.get(5).value);
            pins.get(9).setValue(!pins.get(8).value);
            pins.get(11).setValue(!pins.get(10).value);
            pins.get(13).setValue(!pins.get(12).value);
        }
    }

    private class IC7486 extends ICBase {
        IC7486(double x, double y) {
            super("7486 XOR", x, y);
            setPinType(7, PinType.GROUND); setPinType(14, PinType.POWER);
            int[][] map = {{1, 2, 3}, {4, 5, 6}, {9, 10, 8}, {12, 13, 11}};
            for (int[] m : map) { setPinType(m[0], PinType.INPUT); setPinType(m[1], PinType.INPUT); setPinType(m[2], PinType.OUTPUT); }
        }
        void updateLogic() {
            if (!isPowered()) { for (int p : new int[]{3, 6, 8, 11}) pins.get(p).setValue(false); return; }
            pins.get(3).setValue(pins.get(1).value ^ pins.get(2).value);
            pins.get(6).setValue(pins.get(4).value ^ pins.get(5).value);
            pins.get(8).setValue(pins.get(9).value ^ pins.get(10).value);
            pins.get(11).setValue(pins.get(12).value ^ pins.get(13).value);
        }
    }

    private class IC7487 extends ICBase {
        IC7487(double x, double y) {
            super("7487 XNOR", x, y);
            setPinType(7, PinType.GROUND); setPinType(14, PinType.POWER);
            int[][] map = {{1, 2, 3}, {4, 5, 6}, {9, 10, 8}, {12, 13, 11}};
            for (int[] m : map) { setPinType(m[0], PinType.INPUT); setPinType(m[1], PinType.INPUT); setPinType(m[2], PinType.OUTPUT); }
        }
        void updateLogic() {
            if (!isPowered()) { for (int p : new int[]{3, 6, 8, 11}) pins.get(p).setValue(false); return; }
            pins.get(3).setValue(!(pins.get(1).value ^ pins.get(2).value));
            pins.get(6).setValue(!(pins.get(4).value ^ pins.get(5).value));
            pins.get(8).setValue(!(pins.get(9).value ^ pins.get(10).value));
            pins.get(11).setValue(!(pins.get(12).value ^ pins.get(13).value));
        }
    }

    // ---------- CONNECTION ----------
    private void startConnection(Pin p) {
        if (connectionStart == null) {
            connectionStart = p;
            p.setHighlighted(true);
        } else {
            connectionStart.setHighlighted(false);
            attemptConnect(connectionStart, p);
            connectionStart = null;
        }
    }

    private void attemptConnect(Pin a, Pin b) {
    if (a == b) return;

    // Allow OUTPUT → INPUT in both directions
    if ((a.type == PinType.OUTPUT && b.type == PinType.INPUT) ||
        (b.type == PinType.OUTPUT && a.type == PinType.INPUT)) {
        createWire(a.type == PinType.OUTPUT ? a : b, a.type == PinType.INPUT ? a : b);
        evaluateAll(); // update immediately after connection
        return;
    }

    // Allow OUTPUT → LED INPUT (direct)
    if ((a.type == PinType.OUTPUT && b.owner == null && b.type == PinType.INPUT) ||
        (b.type == PinType.OUTPUT && a.owner == null && a.type == PinType.INPUT)) {
        createWire(a.type == PinType.OUTPUT ? a : b, a.type == PinType.INPUT ? a : b);
        evaluateAll();
        return;
    }

    // Allow chaining from external switch OUTPUT → IC INPUT
    if ((a.owner == null && a.type == PinType.OUTPUT && b.owner != null && b.type == PinType.INPUT) ||
        (b.owner == null && b.type == PinType.OUTPUT && a.owner != null && a.type == PinType.INPUT)) {
        createWire(a.type == PinType.OUTPUT ? a : b, a.type == PinType.INPUT ? a : b);
        evaluateAll();
        return;
    }

    // Block invalid connections
    if ((a.type == PinType.OUTPUT && b.type == PinType.OUTPUT) ||
        (a.type == PinType.INPUT && b.type == PinType.INPUT)) {
        new Alert(Alert.AlertType.ERROR,
            "❌ Invalid connection: You can only connect OUTPUT to INPUT!",
            ButtonType.OK).showAndWait();
        return;
    }

    new Alert(Alert.AlertType.WARNING,
        "⚠ Allowed connections: OUTPUT → INPUT (including IC chaining)",
        ButtonType.OK).showAndWait();
}

    private void clubWireWithPin(Wire existingWire, Pin newPin) {
    Pin endA = existingWire.a;
    Pin endB = existingWire.b;

    // If connecting an input pin
    if (newPin.type == PinType.INPUT) {
        if (endA.type == PinType.OUTPUT) createWire(endA, newPin);
        else if (endB.type == PinType.OUTPUT) createWire(endB, newPin);
        else createWire(endA, newPin); // safe default
    }
    // If connecting an output pin
    else if (newPin.type == PinType.OUTPUT) {
        if (endA.type == PinType.INPUT) createWire(newPin, endA);
        if (endB.type == PinType.INPUT) createWire(newPin, endB);
    } else {
        Alert al = new Alert(Alert.AlertType.ERROR, "Invalid wire clubbing!", ButtonType.OK);
        al.showAndWait();
        flashWire(existingWire);
        return;
    }

    evaluateAll();
}
private void flashWire(Wire w) {
    Color original = (Color) w.path.getStroke();
    Timeline t = new Timeline(
        new KeyFrame(Duration.ZERO, new KeyValue(w.path.strokeProperty(), Color.YELLOW)),
        new KeyFrame(Duration.seconds(0.4), new KeyValue(w.path.strokeProperty(), original))
    );
    t.play();
}


    private void createWire(Pin from, Pin to) {
        Wire w = new Wire(from, to);
        wires.add(w);
        for (Wire ww : wires) ww.redraw();
        evaluateAll();
    }

    // ---------- TRUTH TABLE ----------
 private void showTruthTable() {
    // Find only connected switches and LEDs (active nodes)
    List<ExternalSwitch> activeInputs = externalSwitches.stream()
        .filter(sw -> !sw.pin.connections.isEmpty())
        .collect(Collectors.toList());
    List<ExternalLED> activeOutputs = externalLEDs.stream()
        .filter(led -> !led.pin.connections.isEmpty())
        .collect(Collectors.toList());

    if (activeInputs.isEmpty() || activeOutputs.isEmpty()) {
        new Alert(Alert.AlertType.WARNING, "⚠ Connect at least one input and one output first!").showAndWait();
        return;
    }

    int nInputs = activeInputs.size();
    int combos = 1 << nInputs;

    TableView<Map<String, String>> table = new TableView<>();

    // Use real names for columns based on each component's label
    for (ExternalSwitch sw : activeInputs) {
        String label = ((Text) sw.group.getChildren().stream()
            .filter(n -> n instanceof Text)
            .findFirst().orElse(new Text("IN"))).getText();
        TableColumn<Map<String, String>, String> col = new TableColumn<>(label);
        col.setCellValueFactory(data -> new javafx.beans.property.SimpleStringProperty(data.getValue().get(label)));
        table.getColumns().add(col);
    }

    for (ExternalLED led : activeOutputs) {
        String label = ((Text) led.group.getChildren().stream()
            .filter(n -> n instanceof Text)
            .findFirst().orElse(new Text("OUT"))).getText();
        TableColumn<Map<String, String>, String> col = new TableColumn<>(label);
        col.setCellValueFactory(data -> new javafx.beans.property.SimpleStringProperty(data.getValue().get(label)));
        table.getColumns().add(col);
    }

    // Save original input states
    boolean[] originalStates = new boolean[nInputs];
    for (int i = 0; i < nInputs; i++) originalStates[i] = activeInputs.get(i).pin.value;

    // Generate all combinations
    for (int mask = 0; mask < combos; mask++) {
        Map<String, String> row = new LinkedHashMap<>();

        // Apply each input combination
        for (int i = 0; i < nInputs; i++) {
            boolean value = ((mask >> i) & 1) == 1;
            ExternalSwitch sw = activeInputs.get(i);
            sw.tb.setSelected(value);
            sw.tb.setText(value ? "1" : "0");
            sw.node.setFill(value ? Color.LIMEGREEN : Color.DARKRED);
            sw.pin.setValue(value);
            String label = ((Text) sw.group.getChildren().stream()
                .filter(n -> n instanceof Text)
                .findFirst().orElse(new Text("IN"))).getText();
            row.put(label, value ? "1" : "0");
        }

        evaluateAll();

        // Capture corresponding outputs
        for (ExternalLED led : activeOutputs) {
            String label = ((Text) led.group.getChildren().stream()
                .filter(n -> n instanceof Text)
                .findFirst().orElse(new Text("OUT"))).getText();
            row.put(label, led.pin.value ? "1" : "0");
        }

        table.getItems().add(row);
    }

    // Restore inputs
    for (int i = 0; i < nInputs; i++) {
        ExternalSwitch sw = activeInputs.get(i);
        boolean v = originalStates[i];
        sw.tb.setSelected(v);
        sw.tb.setText(v ? "1" : "0");
        sw.node.setFill(v ? Color.LIMEGREEN : Color.DARKRED);
        sw.pin.setValue(v);
    }

    evaluateAll();

    Dialog<Void> d = new Dialog<>();
    d.setTitle("Truth Table (Live Connections)");
    d.getDialogPane().setContent(table);
    d.getDialogPane().getButtonTypes().add(ButtonType.CLOSE);
    d.setResizable(true);
    d.showAndWait();
}


        // complete evaluateAll, pinSceneCenter, save/load and main to finish the file
    // ---------- EVALUATION ----------
 // ---------- EVALUATION ----------
private void evaluateAll() {
    // If power is OFF — simulate full shutdown
    if (!powerToggle.isSelected()) {
        // 1️⃣ Force all IC outputs LOW
        for (ICBase ic : new ArrayList<>(ics)) {
            for (Map.Entry<Integer, Pin> en : ic.pins.entrySet()) {
                if (en.getValue().type == PinType.OUTPUT) en.getValue().value = false;
            }
        }

        // 2️⃣ Turn off all LEDs (outputs)
        for (ExternalLED led : externalLEDs) {
            led.pin.value = false;
            led.refresh();
        }

        // 3️⃣ Turn off all input indicators (switch LEDs)
        for (ExternalSwitch sw : externalSwitches) {
            sw.node.setFill(Color.DARKRED);
        }

        // 4️⃣ Reset disconnected LEDs and update wires
        resetDisconnectedLEDs();
        for (Wire w : wires) w.redraw();
        return;
    }

    // If power is ON — run normal logic simulation
    for (ICBase ic : ics) ic.updateLogic();

    // Refresh output LEDs
    for (ExternalLED led : externalLEDs) led.refresh();

    // Restore switch LED color (Green = ON)
    for (ExternalSwitch sw : externalSwitches) {
        sw.node.setFill(sw.tb.isSelected() ? Color.LIMEGREEN : Color.DARKRED);
    }

    resetDisconnectedLEDs();

    // Redraw wires to stay aligned with components
    for (Wire w : wires) w.redraw();
}


    // ---------- UTILITIES ----------
    private Point2D pinSceneCenter(Pin p) {
        Bounds b;
        if (p.owner == null) {
            // external switches
            for (ExternalSwitch s : externalSwitches) {
                if (s.pin == p) {
                    b = s.node.localToScene(s.node.getBoundsInLocal());
                    return new Point2D((b.getMinX() + b.getMaxX()) / 2, (b.getMinY() + b.getMaxY()) / 2);
                }
            }
            // external leds
            for (ExternalLED l : externalLEDs) {
                if (l.pin == p) {
                    b = l.node.localToScene(l.node.getBoundsInLocal());
                    return new Point2D((b.getMinX() + b.getMaxX()) / 2, (b.getMinY() + b.getMaxY()) / 2);
                }
            }
            // power/gnd
            if (vccNode != null && vccNode.pin == p) {
                b = vccNode.circle.localToScene(vccNode.circle.getBoundsInLocal());
                return new Point2D((b.getMinX() + b.getMaxX()) / 2, (b.getMinY() + b.getMaxY()) / 2);
            }
            if (gndNode != null && gndNode.pin == p) {
                b = gndNode.circle.localToScene(gndNode.circle.getBoundsInLocal());
                return new Point2D((b.getMinX() + b.getMaxX()) / 2, (b.getMinY() + b.getMaxY()) / 2);
            }
            return new Point2D(0, 0);
        } else {
            Circle c = p.visual;
            b = c.localToScene(c.getBoundsInLocal());
            return new Point2D((b.getMinX() + b.getMaxX()) / 2, (b.getMinY() + b.getMaxY()) / 2);
        }
    }

    // ---------- SAVE / LOAD ----------
    private String ownerKey(Pin p) {
        if (p.owner == null) {
            for (int i = 0; i < externalSwitches.size(); i++) if (externalSwitches.get(i).pin == p) return "SW" + i;
            for (int i = 0; i < externalLEDs.size(); i++) if (externalLEDs.get(i).pin == p) return "LED" + i;
            if (vccNode != null && vccNode.pin == p) return "VCC";
            if (gndNode != null && gndNode.pin == p) return "GND";
            return "EXT";
        } else {
            for (int i = 0; i < ics.size(); i++) if (ics.get(i) == p.owner) return "IC" + i;
            return "IC?";
        }
    }

    private Pin findPinByKey(String key, int pinNumber, Map<String, ICBase> icMap, Map<String, ExternalSwitch> swMap, Map<String, ExternalLED> ledMap) {
        if ("VCC".equals(key)) return vccNode.pin;
        if ("GND".equals(key)) return gndNode.pin;
        if (key.startsWith("IC")) {
            ICBase ic = icMap.get(key);
            if (ic != null) return ic.pins.get(pinNumber);
        }
        if (key.startsWith("SW")) {
            ExternalSwitch s = swMap.get(key);
            if (s != null) return s.pin;
        }
        if (key.startsWith("LED")) {
            ExternalLED l = ledMap.get(key);
            if (l != null) return l.pin;
        }
        return null;
    }

    private void saveCircuit(Stage stage) {
        FileChooser fc = new FileChooser();
        fc.setTitle("Save Circuit");
        fc.getExtensionFilters().add(new FileChooser.ExtensionFilter("Virtual IC file", "*.vic"));
        File f = fc.showSaveDialog(stage);
        if (f == null) return;
        try (PrintWriter pw = new PrintWriter(new FileWriter(f))) {
            pw.println("POWER=" + powerToggle.isSelected());
            // switches
            for (int i = 0; i < externalSwitches.size(); i++) {
                ExternalSwitch s = externalSwitches.get(i);
                pw.println(String.format("SW,%d,%.2f,%.2f,%b", i, s.group.getLayoutX(), s.group.getLayoutY(), s.tb.isSelected()));
            }
            // leds
            for (int i = 0; i < externalLEDs.size(); i++) {
                ExternalLED l = externalLEDs.get(i);
                pw.println(String.format("LED,%d,%.2f,%.2f", i, l.group.getLayoutX(), l.group.getLayoutY()));
            }
            // ics
            for (int i = 0; i < ics.size(); i++) {
                ICBase ic = ics.get(i);
                pw.println(String.format("IC,%d,%s,%.2f,%.2f", i, ic.title.getText(), ic.group.getLayoutX(), ic.group.getLayoutY()));
            }
            // wires
            for (Wire w : wires) {
                String a = ownerKey(w.a);
                String b = ownerKey(w.b);
                pw.println(String.format("WIRE,%s,%s,%d,%d", a, b, w.a.number, w.b.number));
            }
            pw.flush();
        } catch (IOException ex) {
            ex.printStackTrace();
        }
    }

    private void loadCircuit(Stage stage) {
        FileChooser fc = new FileChooser();
        fc.setTitle("Load Circuit");
        fc.getExtensionFilters().add(new FileChooser.ExtensionFilter("Virtual IC file", "*.vic"));
        File f = fc.showOpenDialog(stage);
        if (f == null) return;
        try (BufferedReader br = new BufferedReader(new FileReader(f))) {
            // clear current
            for (Wire w : new ArrayList<>(wires)) w.remove();
            wires.clear();
            for (ICBase ic : new ArrayList<>(ics)) { board.getChildren().remove(ic.group); ics.remove(ic); }
            for (ExternalLED l : new ArrayList<>(externalLEDs)) { board.getChildren().remove(l.group); externalLEDs.remove(l); }
            for (ExternalSwitch s : new ArrayList<>(externalSwitches)) { board.getChildren().remove(s.group); externalSwitches.remove(s); }

            Map<String, ICBase> icMap = new HashMap<>();
            Map<String, ExternalSwitch> swMap = new HashMap<>();
            Map<String, ExternalLED> ledMap = new HashMap<>();

            List<String> wireLines = new ArrayList<>();

            String line;
            while ((line = br.readLine()) != null) {
                if (line.trim().isEmpty()) continue;
                if (line.startsWith("POWER=")) {
                    powerToggle.setSelected(Boolean.parseBoolean(line.substring(6)));
                    stylePowerButton();
                    continue;
                }
                String[] toks = line.split(",");
                switch (toks[0]) {
                    case "SW" -> {
                        int idx = Integer.parseInt(toks[1]);
                        double lx = Double.parseDouble(toks[2]);
                        double ly = Double.parseDouble(toks[3]);
                        boolean on = Boolean.parseBoolean(toks[4]);
                        ExternalSwitch s = new ExternalSwitch(lx, ly, idx + 1);
                        s.group.setLayoutX(lx); s.group.setLayoutY(ly);
                        s.tb.setSelected(on); s.tb.setText(on ? "1" : "0");
                        s.node.setFill(on ? Color.LIMEGREEN : Color.DARKRED);
                        s.pin.setValue(on);
                        externalSwitches.add(s);
                        board.getChildren().add(s.group);
                        swMap.put("SW" + idx, s);
                    }
                    case "LED" -> {
                        int idx = Integer.parseInt(toks[1]);
                        double lx = Double.parseDouble(toks[2]);
                        double ly = Double.parseDouble(toks[3]);
                        ExternalLED l = new ExternalLED(lx, ly, idx + 1);
                        l.group.setLayoutX(lx); l.group.setLayoutY(ly);
                        externalLEDs.add(l);
                        board.getChildren().add(l.group);
                        ledMap.put("LED" + idx, l);
                    }
                    case "IC" -> {
                        int idx = Integer.parseInt(toks[1]);
                        String name = toks[2];
                        double lx = Double.parseDouble(toks[3]);
                        double ly = Double.parseDouble(toks[4]);
                        ICBase ic;
                        switch (name) {
                            case "7400", "7400 NAND" -> ic = new IC7400(lx, ly);
                            case "7402", "7402 NOR" -> ic = new IC7402(lx, ly);
                            case "7408", "7408 AND" -> ic = new IC7408(lx, ly);
                            case "7432", "7432 OR" -> ic = new IC7432(lx, ly);
                            case "7404", "7404 NOT" -> ic = new IC7404(lx, ly);
                            case "7486", "7486 XOR" -> ic = new IC7486(lx, ly);
                            case "7487", "7487 XNOR" -> ic = new IC7487(lx, ly);
                            default -> ic = new IC7400(lx, ly);
                        }
                        ic.group.setLayoutX(lx); ic.group.setLayoutY(ly);
                        ics.add(ic);
                        board.getChildren().add(ic.group);
                        icMap.put("IC" + idx, ic);
                    }
                    case "WIRE" -> wireLines.add(line);
                }
            }

            // second pass: add wires
            for (String wln : wireLines) {
                String[] toks = wln.split(",");
                if (toks.length < 5) continue;
                String aStr = toks[1], bStr = toks[2];
                int pinA = Integer.parseInt(toks[3]);
                int pinB = Integer.parseInt(toks[4]);
                Pin pa = findPinByKey(aStr, pinA, icMap, swMap, ledMap);
                Pin pb = findPinByKey(bStr, pinB, icMap, swMap, ledMap);
                if (pa != null && pb != null) createWire(pa, pb);
            }

            evaluateAll();
        } catch (IOException ex) {
            ex.printStackTrace();
        }
    }
    // ---------- Drag Helpers ----------
private static class Delta {
    double x, y;
}

private void makeDraggable(Group g) {
    final Delta dragDelta = new Delta();
    g.setOnMousePressed(e -> {
        if (e.getButton() != MouseButton.PRIMARY) return;
        dragDelta.x = e.getSceneX() - g.getLayoutX();
        dragDelta.y = e.getSceneY() - g.getLayoutY();
    });
    g.setOnMouseDragged(e -> {
        if (e.getButton() != MouseButton.PRIMARY) return;
        g.setLayoutX(e.getSceneX() - dragDelta.x);
        g.setLayoutY(e.getSceneY() - dragDelta.y);
        for (Wire w : wires) w.redraw(); // refresh wires dynamically
    });
}

private void makeDraggable(Pane p) {
    final Delta dragDelta = new Delta();
    p.setOnMousePressed(e -> {
        if (e.getButton() != MouseButton.PRIMARY) return;
        dragDelta.x = e.getSceneX() - p.getLayoutX();
        dragDelta.y = e.getSceneY() - p.getLayoutY();
    });
    p.setOnMouseDragged(e -> {
        if (e.getButton() != MouseButton.PRIMARY) return;
        p.setLayoutX(e.getSceneX() - dragDelta.x);
        p.setLayoutY(e.getSceneY() - dragDelta.y);
        for (Wire w : wires) w.redraw(); // refresh wire geometry
    });
}


    // ---------- MAIN ----------
    public static void main(String[] args) {
        launch(args);
    }
}
