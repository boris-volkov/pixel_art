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
- GIF/sequence exports keep transparency; “restore to background” disposal prevents frame ghosts.
- `save-sequence` creates a folder matching the base name and places numbered files inside.
- Active layer frames stay aligned: selecting/adding/duplicating frames syncs other layers to the same index modulo their lengths; playback loops over the longest cycle.
