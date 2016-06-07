This is the source code for the JARM emulator, the OC-ARM Minecraft/OpenComputers mod, and the simulator for the latter. Project files are not included.

To build the simulator, you require the following directories:

- `src/name/bizna/jarm`  
  The emulator core.
- `src/name/bizna/ocarmsim`  
  The simulator application, and the fake OC-ARM scaffold.
- `src/net/minecraft/nbt`  
  Stub of a bit of Minecraft API, to make the section of OpenComputers API we use work.

You must also build against the OpenComputers API.

To build the Minecraft mod, you require the following directories:

- `src/name/bizna/jarm`  
  The emulator core.
- `src/name/bizna/ocarm`  
  The OpenComputers architecture module, and the Minecraft mod that enables it.

Provide your own Forge source, etc. Should build against Minecraft 1.7 and 1.8, but is only tested against 1.7.

To embed the emulator into your own project, you need only the classes in the `src/name/bizna/jarm` directory. I'm no Java expert, but you can probably link against the simulator JAR.
