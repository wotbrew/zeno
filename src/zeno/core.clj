(ns zeno.core
  "Core zeno API, provides a context for developing REPL friendly LibGDX applications."
  (:require [zeno.protocols :as p]
            [clojure.java.io :as io]
            [clojure.pprint :as pp]
            [clojure.repl])
  (:import (com.badlogic.gdx Gdx Application ApplicationListener InputProcessor)
           (com.badlogic.gdx.backends.lwjgl LwjglApplication)
           (clojure.lang IFn PersistentQueue IDeref)
           (com.badlogic.gdx.graphics Texture)
           (com.badlogic.gdx.files FileHandle)
           (java.io InputStream StringWriter)
           (java.net URL)
           (com.badlogic.gdx.utils Disposable Align)
           (java.util UUID)
           (zeno Zeno)))

(set! *warn-on-reflection* true)
(set! *unchecked-math* :warn-on-boxed)

(defonce ^:private handlers (atom #{}))
(defonce ^:private drawing (atom nil))
(defonce ^:private configuration (atom nil))
(defonce ^:private gpu-resources (atom {}))

(defn !!configure!!
  "Call once at the very start of your application to provide certain configuration options
  that cannot be changed once the application has started, due to limitations in LibGDX."
  [config]
  (assert (nil? Gdx/app) "Cannot configure an application that has already started.")
  (reset! configuration config)
  nil)

(defn- safe-handle
  [handler event]
  (try
    (p/handle handler event)
    (catch Throwable e
      (.printStackTrace e System/err))))

(defn- handle
  [event]
  (run! #(safe-handle % event) @handlers))

(defn- start-gdx
  []
  (let [input-processor
        (reify InputProcessor
          (keyDown [this gdx-key]
            (handle {:zeno/event :zeno.events/input-down
                     :zeno/gdx-key gdx-key
                     :zeno/delta-secs (.getDeltaTime Gdx/graphics)})
            true)
          (keyUp [this gdx-key]
            (handle {:zeno/event :zeno.events/input-up
                     :zeno/gdx-key gdx-key})
            true)
          (keyTyped [this c]
            (handle {:zeno/event :zeno.events/char-typed
                     :zeno/char-typed c})
            true)
          (touchDown [this x y gdx-pointer gdx-button]
            true)
          (touchUp [this x y gdx-pointer gdx-button]
            true)
          (touchDragged [this x y gdx-pointer]
            true)
          (mouseMoved [this x y]
            (handle {:zeno/event :zeno.events/mouse-moved
                     :zeno/mouse [x y]})
            true)
          (scrolled [this gdx-scroll]
            (handle {:zeno/event :zeno.events/mouse-scrolled
                     :zeno/scroll (* 60.0 gdx-scroll (.getDeltaTime Gdx/graphics))})
            true))

        thread-bindings (get-thread-bindings)

        listener
        (reify ApplicationListener
          (create [this]
            (push-thread-bindings thread-bindings)
            (Zeno/init)
            (.setInputProcessor Gdx/input input-processor)
            (handle {:zeno/event :zeno.events/window-resized
                     :zeno/window-size [(.getWidth Gdx/graphics) (.getHeight Gdx/graphics)]})
            (handle {:zeno/event :zeno.events/mouse-moved
                     :zeno/mouse [(.getX Gdx/input) (.getY Gdx/input)]}))
          (resize [this w h]
            (handle {:zeno/event :zeno.events/window-resized
                     :zeno/window-size [w h]}))
          (render [this]
            (handle {:zeno/event :zeno.events/new-frame
                     :zeno/delta (.getDeltaTime Gdx/graphics)})
            (try
              (Zeno/begin)
              (when-some [d @drawing]
                (p/draw d 0 0 (.getWidth Gdx/graphics) (.getHeight Gdx/graphics)))
              (catch Throwable e
                (handle {:zeno/event :zeno.events/render-error
                         :zeno/ex e})
                (let [_ (require 'clojure.repl)
                      writer (StringWriter.)
                      _ (binding [*err* writer] (clojure.repl/pst e))
                      s (str writer)]
                  (Zeno/drawDefaultString s 0.0 0.0 (.getWidth Gdx/graphics) Align/left)))
              (finally
                (Zeno/end))))
          (pause [this])
          (resume [this])
          (dispose [this]))]

    (LwjglApplication. listener)))

(defn- ^Application gdx
  "Returns the LibGDX Application"
  []
  (or Gdx/app
      (locking Gdx
        (or Gdx/app (start-gdx)))))

(defn dispatch
  "Runs (f) on the LibGDX thread, if you are not already on it. Uncaught exceptions will emit the dispatch-error event,
  always returns nil."
  [f]
  (if (= (Zeno/getGlThread) (Thread/currentThread))
    (try
      (f)
      (catch Throwable ex
        (handle {:zeno/event :zeno.events/dispatch-error
                 :zeno/ex    ex})))
    (.postRunnable
      (gdx)
      (bound-fn []
        (try
          (f)
          (catch Throwable ex
            (handle {:zeno/event :zeno.events/dispatch-error
                     :zeno/ex    ex}))))))
  nil)

(defn silence!
  "Stops playing the audio."
  [audio]
  (gdx))

(defn play!
  "Plays some audio e.g a sound or music."
  [audio]
  (gdx))

(defn show!
  "Draws to the screen. Replaces whatever is currently on the screen. Takes a Drawable or DrawableSource."
  [drawable]
  (gdx)
  (if (satisfies? p/Drawable drawable)
    (reset! drawing drawable)
    (show! (p/drawable drawable)))
  nil)

(defn draw!
  [drawable x y w h]
  (p/draw drawable x y w h))

(defn attach!
  "Attaches a handler to zeno so that it starts receiving events."
  [handler]
  (gdx)
  (swap! handlers conj handler)
  nil)

(defn detach!
  "Detaches a handler from zeno so that it will stop receiving events.

  Not guaranteed to stop immediately, though it should stop within 1 frame."
  [handler]
  (swap! handlers disj handler)
  nil)

(defmulti respond
  "Asks the game to respond to the event. Returns a new game."
  {:arglists '([g event])}
  (fn respond-dispatch [g event] (:zeno/event event)))

(defmethod respond :default [g _] g)

(defn game-loop
  "Returns a new game loop object, it is both Drawable and Handler
  so you can start drawing the game over time with (draw! game-loop) and handle input
  with (attach! game-loop).

  Call (deref game-loop) to sample the value of the game right now."
  [g]
  (let [a (agent g :error-mode :continue)
        playing-fx (atom #{})
        fx-cb
        (fn fx-cb
          ([f]
           (send a f) nil)
          ([f blocking]
           (send a f)
           (when blocking
             (await a))
           nil))
        safe-respond
        (fn safe-respond [g event]
          (try
            (respond g event)
            (catch Throwable ex
              ;; what to do here, as this is a fail loop.
              (if (= :zeno.events/respond-error (:zeno/event event))
                g
                (respond g {:zeno/event :zeno.events/respond-error
                            :zeno/event-data event
                            :zeno/ex ex})))))]
    (reify
      IDeref
      (deref [this] @a)
      p/Handler
      (handle [this event]
        (case (:zeno/event event)
          :zeno.events/new-frame
          (let [delta (double (:zeno/delta event 0.0))]
            (when (> delta 4e-3)
              (await-for 4 a))
            (let [fx (:zeno/fx-queue @a PersistentQueue/EMPTY)
                  ;; for now we just use a set of currently playing fx.
                  new-fx (remove @playing-fx fx)]
              (reset! playing-fx fx)
              (run! #(try
                       (p/run-fx % fx-cb)
                       (catch Throwable ex
                         (fx-cb (fn [g] (respond g {:zeno/event :zeno.events/fx-error
                                                    :zeno/fx    %
                                                    :zeno/ex    ex})))))
                    new-fx)))
          nil)
        (send a safe-respond event))
      p/DrawableSource
      (drawable [this]
        (fn draw-game [x y w h]
          )))))

(defn elapse
  "Returns a new game where secs have passed, runs functions queued with (defer).

  You should call this every frame with the delta-time.

  Responsible for the key :zeno/elapsed in the game.

  Emits n :zeno.events/elapsed events for each discrete state reached as deferred functions are run."
  [g secs]
  (if (<= (double secs) 0.0)
    g
    (let [elapsed (:zeno/elapsed g 0.0)
          elapsed (double elapsed)
          secs (double secs)
          q (::deferred g)
          q (or q (sorted-map))
          entry (first (subseq q <= (+ elapsed secs)))]
      (if-some [[run-time run-list] entry]
        (let [now-elapsed run-time
              now-elapsed (double now-elapsed)
              delta (- now-elapsed elapsed)
              secs-remaining (- secs delta)
              q (dissoc q run-time)
              g (assoc g :zeno/elapsed now-elapsed
                         ::deferred q)
              g (reduce (fn [g f] (f g)) g run-list)
              g (respond g {:zeno/event :zeno.events/elapsed
                            :zeno/delta delta})]
          (recur g secs-remaining))
        (let [now-elapsed (+ elapsed secs)
              g (assoc g :zeno/elapsed now-elapsed)
              g (respond g {:zeno/event :zeno.events/elapsed
                            :zeno/delta secs})]
          g)))))

(defn defer
  "Enqueues a function of game to game to run when at least secs elapses via (elapse)."
  ([g secs f]
   (if (<= (double secs) 0.0)
     (f g)
     (let [q (::deferred g)
           q (or q (sorted-map))
           elapsed (:zeno/elapsed g 0.0)
           elapsed (double elapsed)
           secs (double secs)
           run-time (+ elapsed secs)
           run-list (get q run-time [])
           run-list (conj run-list f)
           q (assoc q run-time run-list)]
       (assoc g ::deferred q))))
  ([g secs f & args]
   (defer g secs #(apply f % args))))

(extend-protocol p/Handler
  IFn
  (handle [this event] (this event)))

(extend-protocol p/Fx
  IFn
  (run-fx [this updater] (this updater)))

(extend-protocol p/Drawable
  nil
  (draw [this x y w h])
  IDeref
  (draw [this x y w h] (p/draw @this x y w h))
  IFn
  (draw [this x y w h] (this x y w h)))

(defn ^FileHandle
  input-stream-handle
  [^InputStream in]
  (let [name (str (UUID/randomUUID))]
    (proxy [FileHandle] []
      (name [] name)
      (toString [] name)
      (read [] in)
      (length [] 0))))

(defn free
  [id]
  (when-some [resource (-> @gpu-resources (get id))]
    (swap! gpu-resources dissoc id)
    (dispatch
      (fn []
        (p/free resource)))))

(defn free-all
  []
  (dispatch (fn [] (run! free (keys @gpu-resources)))))

;; general extensions
(extend-type Disposable
  p/GPUResource
  (free [this] (.dispose this)))

;; texture extensions
(extend-type Texture
  p/Drawable
  (draw [this x y w h]
    (Zeno/drawTexture this x y w h)))

;; string extension
(extend-type String
  p/Drawable
  (draw [this x y w _]
    (Zeno/drawDefaultString this x y w Align/center)))

;; object extension via pprinter
(extend-type Object
  p/DrawableSource
  (drawable [this]
    (let [sw (StringWriter.)
          _ (binding [*print-length* 1000
                      *print-level* 10]
              (pp/pprint this sw))
          s (.toString sw)]
      s)))

;; url extensions
(extend-type URL
  p/DrawableSource
  (drawable [this]
    (or (get @gpu-resources this)
        (locking gpu-resources
          (or (get @gpu-resources this))
          (with-open [in (io/input-stream this)]
            (let [fh (input-stream-handle in)
                  p (promise)
                  _ (dispatch (fn [] (deliver p (try (Texture. fh) (catch Throwable e e)))))
                  texture (deref p 10000 nil)]

              (if (instance? Texture texture)
                (swap! gpu-resources assoc this texture)
                (throw (Exception. "Could not load Texture from URL" texture)))

              (get @gpu-resources this)))))))

(defn set-window-title
  "Sets the window title to something else."
  [s]
  (dispatch (fn [] (.setTitle Gdx/graphics (str s)))))

(defn set-window-size
  "Changes the size of the window."
  [w h]
  (dispatch (fn [] (.setWindowedMode Gdx/graphics (int w) (int h)))))
