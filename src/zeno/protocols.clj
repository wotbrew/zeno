(ns zeno.protocols)

(defprotocol Drawable
  :extend-via-metadata true
  (draw [this] "Draws the object to the screen. assume the gl thread if calling via (zeno/draw)"))

(defprotocol DrawableSource
  :extend-via-metadata true
  (drawable [this] "Returns a Drawable instance for the given object."))

(defprotocol Audio
  :extend-via-metadata true
  (play [this] "Plays the audio"))

(defprotocol Handler
  :extend-via-metadata true
  (handle [this event] "Handles the given event, which is a map with a :zeno/event key, expects some side effect. Guaranteed to be called from the gl thread."))

(defprotocol Fx
  :extend-via-metadata true
  (run-fx [this updater] "Implement for FX"))