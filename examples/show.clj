(ns show
  (:require [zeno.core :as zeno])
  (:import (zeno Zeno)
           (com.badlogic.gdx.utils Align)))

(zeno/show! "(show!) example, run the comment forms!")

;; what show does is take a Drawable or DrawableSource
;; and continues to draw it to the viewport rectangle
;; every frame this way you can preview your drawables.

;; your game loop object is going to be drawable, see the game-loop example.

(comment
  ;; you can show strings
  (zeno/show! "Strings")

  ;; .png images from a file or url
  (zeno/show! (URL. "https://clojure.org/images/clojure-logo-120b.png"))

  ;; functions of rectangle co-ordinates are drawable via invocation.
  ;; this allows you to draw anything really with both zeno and LibGDX directly.

  ;; You can also use LibGDX to draw what ever you want.
  ;; Some convenience fns are available under Zeno/draw*,
  ;; as well as (zeno/draw!)
  (zeno/show!
    (fn [x y w h]
      (zeno/draw! "Default string" x y w h)
      (Zeno/drawDefaultString "Left Aligned String" x (+ 16 y) w Align/left)))

  ;; thrown exceptions will be drawn to the screen
  ;; so that you can debug them without them being printed
  ;; every frame.
  (zeno/show!
    (fn [_ _ _ _]
      (throw (Exception. "Error")))))