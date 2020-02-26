# zeno

A REPL friendly 2D game development library for Clojure.

## At a glance

- REPL friendly, thread-safe API.
- Your game is a value, immutable and functions on it provided by zeno
 are pure.
- Simple graph architecture for game entities.
- Allows access to LibGDX, but makes some decisions for you
  to make it easier to develop basic 2d games.

## Hello world
```clojure
(require '[zeno.core :as zeno])

(zeno/show! "Hello world")
```

## Game Architecture

In zeno, your game is a value - in fact 
a plain hash map. All game logic is to be implemented as pure functions.

Utilities are provided to support side effects, drawing and playing audio.

Events are consumed by your game with a game loop.

## License

MIT License

Copyright (c) 2020 Dan Stone

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all
copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
SOFTWARE.