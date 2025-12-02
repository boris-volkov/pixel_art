# Pixel Art Editor (Swing)

Lightweight Java/Swing pixel editor with retro UI, per-layer animation timelines, and console commands.

## Running
1. Install a JDK (11+ recommended).
2. From the repo root:
   ```sh
   javac -d out src/*.java
   java -cp out PixelArtApp
   ```
   (You can also `javac src/*.java && java -cp src PixelArtApp` since there are no packages.)

## UI quick tour
- **Canvas**: paint pixels; Alt-click picks a color; Shift constrains strokes; Arrow keys pan when the console isn’t focused; `Ctrl+Z` undo.
- **Tools**: Brush, Eraser, Stamp (16×16 sub-canvas), Fill, Blur brush, Move (drag active layer content).
- **Brush size**: mouse wheel, `[` / `]`, or the slider.
- **Layers**: three stacked layers with visibility toggles and up-arrow reorder. Active layer is highlighted.
- **Animation timeline**: per-layer frames (Play/Stop, Onion, Add, Delete, Duplicate). Playback advances all animated layers; GIF export uses the least-common-multiple of frame counts.
- **Console (bottom)**: click to focus; `Esc` toggles focus. Enter commands here.

## Console commands
- `save <file.png>` — save current composite PNG (with transparency).
- `save-sequence <base.png>` — export numbered PNGs to a folder named after `<base>`.
- `save-gif <file.gif>` — export animated GIF; total loop length = LCM of layer frame counts; frame delay from current framerate.
- `save-project <file>` / `load-project <file>` — serialize/restore full project (layers, frames, names, visibility, colors, zoom, etc.).
- `load <file.png>` — load a square PNG into the canvas.
- `new <size>` or `new <w> <h>` — create a new blank canvas.
- `resolution` — print current canvas dimensions.
- `flip h|v` — flip horizontally or vertically.
- `blur gaussian <radius>` — apply Gaussian blur to the active layer.
- `blur motion <angleDeg> <amount>` — motion blur (angle uses standard trig orientation).
- `dither floyd` | `dither ordered` — apply dithering.
- `resample <factor>` — scale the canvas and all frames by an integer factor (>1).
- `background <r> <g> <b>` — set viewport background color (to preview sprites over a flat color).
- `calc <expr>` — simple Lisp-style math, e.g., `calc (+ 1 2)` or `calc (* (+ 1 2) 3)`.
- `animate [layer]` — open the animation panel (optionally set the active layer by name or L#).
- `framerate <fps>` — set playback/export fps.
- `duplicate` — duplicate the current frame (inserts after the current one).
- `rename L# <name>` — rename a layer.
- `onion` — toggle onion skinning.
- `exit` — quit.

## Tips
- Stamp preview and painting can overflow the canvas edges; only the visible portion is drawn.
- GIF/sequence exports keep transparency; "restore to background" disposal prevents frame ghosts.
- `save-sequence` creates a folder matching the base name and places numbered files inside.
- Active layer frames stay aligned: selecting/adding/duplicating frames syncs other layers to the same index modulo their lengths; playback loops over the longest cycle.

## Files

### Root Directory
- **README.md**: This file, containing project description, running instructions, UI tour, console commands, and tips.
- **.gitignore**: Git ignore file to exclude certain files and directories from version control.
- **LICENSE**: License file specifying the terms under which the project can be used.
- **witch.png**: Sample image file, possibly used as an example or icon in the project.
- **src/**: Directory containing all Java source code files.

### Source Code (src/)
- **PixelArtApp.java**: Main application class that initializes the GUI, handles user interactions, manages the overall application state, and contains the entry point.
- **PixelArtController.java**: Controller class intended to mediate between the model and view (currently incomplete with placeholder implementations).
- **PixelArtModel.java**: Model class that holds the core data structures including layers, animation frames, colors, and canvas state.
- **PixelArtView.java**: Interface defining the contract for view implementations in the pixel art editor.
- **SwingPixelArtView.java**: Swing-based implementation of the PixelArtView interface, providing the graphical user interface components.
- **PixelCanvas.java**: Custom JPanel component responsible for rendering the pixel canvas, handling mouse input, tool operations, and undo/redo functionality.
- **PixelArtFileHandler.java**: Utility class for handling file operations such as saving and loading images, projects, GIF animations, and sequences.
- **PixelArtAnimationHandler.java**: Class managing animation playback, frame manipulation, and timeline controls.
- **ActionButton.java**: Custom button component used for various action triggers in the UI.
- **AnimationPanel.java**: Panel component for displaying and controlling animation timelines and frame navigation.
- **CanvasViewport.java**: Viewport component that manages the display and scrolling of the pixel canvas.
- **ColorState.java**: Class managing color state including RGB and HSB color representations and conversions.
- **ConsolePanel.java**: Panel implementing the command console for text-based user input and output.
- **ControlBar.java**: UI component containing controls for tools, brush settings, and layer management.
- **FocusWrap.java**: Utility class for managing focus behavior in UI components.
- **PixelFont.java**: Custom font rendering class for displaying text in a pixel art style.
- **SliderControl.java**: Custom slider component for adjusting numerical values like brush size and color components.
- **StampPanel.java**: Panel for managing the stamp tool, including preview and editing of stamp patterns.
- **TopBar.java**: Top-level bar component with additional controls and menus.
