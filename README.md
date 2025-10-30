# VirtualICTrainerKit
JavaFX-based Virtual IC Trainer for simulating digital logic circuits interactively.
A simple JavaFX project that simulates basic digital logic ICs like AND, OR, NOT, NAND, NOR, XOR, and XNOR gates.  
It helps users design and test digital circuits virtually, just like using a real hardware trainer kit.

 Project Goal
To create a **virtual version** of a digital electronics trainer kit using **Java and JavaFX**,  
allowing students to understand logic gates and circuit behavior without physical components.

 Features
- Simulates popular ICs (7400, 7402, 7404, 7408, 7432, 7486, etc.)
- Real-time logic output when switches are toggled
- User-friendly interface using JavaFX
- Truth table generation for circuits
- Power ON/OFF control
- Simple save/load circuit option


 How to Run
1. Make sure **Java** and **JavaFX** are installed on your system.  
2. Open terminal inside the project folder.  
3. Compile:
   ```bash
   javac --module-path "C:\Program Files\Java\JavaFX\lib" --add-modules javafx.controls,javafx.fxml VirtualICTrainer.java
