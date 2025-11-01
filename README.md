# 🧠 Virtual IC Trainer Kit  
_A JavaFX-based simulator for designing and testing digital logic circuits virtually_

## 📖 Overview  
The **Virtual IC Trainer Kit** is a desktop application that replicates the functionality of a physical digital electronics trainer kit.  
It allows users to design, connect, and simulate logic circuits virtually — eliminating the need for physical hardware setups.

Developed using **Object-Oriented Programming (OOP)** principles and **JavaFX**, this project provides a clean, interactive interface for students and enthusiasts to explore digital logic design in a safe, visual, and reusable environment.

## 🎯 Objectives
- Simulate the behavior of common 74xx-series logic ICs (AND, OR, NOT, NAND, NOR, XOR, XNOR).  
- Provide a drag-and-drop interface for building and testing circuits.  
- Allow real-time logic simulation with instant visual feedback.  
- Include features such as automatic truth table generation and circuit save/load options.  
- Offer a cost-free, portable alternative to physical trainer kits.

## 🧩 Features
✅ **Interactive GUI** – Add, move, and connect ICs visually using JavaFX.  
✅ **Real-Time Simulation** – Logic updates dynamically when inputs change.  
✅ **Truth Table Generator** – Automatically compute truth tables for custom circuits.  
✅ **Save/Load Circuits** – Store and reload circuit layouts easily.  
✅ **Power Toggle** – Turn ON/OFF the simulated power supply.  
✅ **Undo/Redo LEDs and Wires** – Modify your workspace with ease.  
✅ **Multiple IC Support** – Includes:
- 7400 NAND  
- 7402 NOR  
- 7404 NOT  
- 7408 AND  
- 7432 OR  
- 7486 XOR  
- 7487 XNOR  

## 🧠 System Architecture
Each circuit element is modeled as an individual Java class:
- `VirtualICTrainer` → Main JavaFX application.  
- `ICBase` → Abstract class defining pin structure and logic behavior.  
- `IC7400`, `IC7402`, etc. → Specific IC implementations extending `ICBase`.  
- `Pin`, `Wire` → Handle connection and signal propagation.  
- `ExternalSwitch`, `ExternalLED` → Simulate input switches and output LEDs.

## 🧮 Example Use
1. Launch the app (after compiling with JavaFX).  
2. Add ICs using toolbar buttons.  
3. Connect input switches and output LEDs using mouse clicks.  
4. Toggle switches and observe real-time logic simulation.  
5. Generate a truth table for your circuit with one click.

## 🚀 Future Scope

- Add sequential circuits like Flip-Flops and Counters  
- Include more ICs such as multiplexers and decoders  
- Add waveform/oscilloscope visualization  
- Implement circuit export and import options  
- Improve the overall user interface and performance  

## 👩‍💻 Author  
**Arha Vijitha Suresh** [@arhavs047](https://github.com/arhavs047)

## 📜 License  
This project is licensed under the **MIT License** — see the [LICENSE](./LICENSE) file for details.

