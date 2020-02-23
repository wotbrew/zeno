(ns zeno.core
  "Core zeno API, provides a context for developing REPL friendly LibGDX applications."
  (:require [zeno.protocols :as p])
  (:import (com.badlogic.gdx Gdx Application ApplicationListener InputProcessor)
           (com.badlogic.gdx.backends.lwjgl LwjglApplication)
           (clojure.lang IFn PersistentQueue IDeref)
           (org.lwjgl.opengl GL11)
           (com.badlogic.gdx.graphics GL20)))

(defonce ^:private handlers (atom #{}))
(defonce ^:private drawing (atom nil))
(defonce ^:private thread (atom nil))
(defonce ^:private configuration (atom nil))

(defn !!configure!!
  "Call once at the very start of your application to provide certain configuration options
  that cannot be changed once the application has started, due to limitations in LibGDX."
  [config]
  (assert (nil? Gdx/app) "Cannot configure an application that has already started.")
  (reset! configuration config)
  nil)

(defn- safe-handle
  [handler event]
  (p/handle handler event))

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
                     :zeno/delta-secs (.getDeltaTime Gdx/graphics)}))
          (keyUp [this gdx-key]
            (handle {:zeno/event :zeno.events/input-up
                     :zeno/gdx-key gdx-key}))
          (keyTyped [this c]
            (handle {:zeno/event :zeno.events/char-typed
                     :zeno/char-typed c}))
          (touchDown [this x y gdx-pointer gdx-button])
          (touchUp [this x y gdx-pointer gdx-button]
            )
          (touchDragged [this x y gdx-pointer]
            )
          (mouseMoved [this x y]
            (handle {:zeno/event :zeno.events/mouse-moved
                     :zeno/mouse [x y]}))
          (scrolled [this gdx-scroll]
            (handle {:zeno/event :zeno.events/mouse-scrolled
                     :zeno/scroll (* 60.0 gdx-scroll (.getDeltaTime Gdx/graphics))})))

        thread-bindings (get-thread-bindings)

        listener
        (reify ApplicationListener
          (create [this]
            (push-thread-bindings thread-bindings)
            (reset! thread (Thread/currentThread))
            (.setInputProcessor Gdx/input input-processor)
            (handle {:zeno/event :zeno.events/window-resized
                     :zeno/window-size [(.getWidth Gdx/graphics) (.getHeight Gdx/graphics)]})
            (handle {:zeno/event :zeno.events/mouse-moved
                     :zeno/mouse [(.getX Gdx/input) (.getY Gdx/input)]}))
          (resize [this w h]
            (handle {:zeno/event :zeno.events/window-resized
                     :zeno/window-size [w h]}))
          (render [this]
            (.glClear GL11 (bit-or GL20/GL_COLOR_BUFFER_BIT GL20/GL_DEPTH_BUFFER_BIT))
            (handle {:zeno/event :zeno.events/new-frame
                     :zeno/delta (.getDeltaTime Gdx/graphics)})
            (when-some [d @drawing]
              (try
                (p/draw d)
                (catch Throwable e
                  (handle {:zeno/event :zeno.events/render-error
                           :zeno/ex e})))))
          (pause [this])
          (resume [this])
          (dispose [this]))]

    (LwjglApplication. listener)))

(defn- ^Application gdx
  "Returns the LibGDX Application"
  []
  (or Gdx/app
      (locking Gdx
        (or Gdx/app (gdx)))))

(defn dispatch
  "Runs (f) on the LibGDX thread, if you are not already on it. Uncaught exceptions will emit the dispatch-error event,
  always returns nil."
  [f]
  (if (= @thread (Thread/currentThread))
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

(defn draw!
  "Draws to the screen. Replaces whatever is currently on the screen. Takes a Drawable or DrawableSource."
  [drawable]
  (gdx)
  (if (satisfies? p/Drawable drawable)
    (reset! drawing drawable)
    (draw! (p/drawable drawable)))
  nil)

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
  with (attach! game-loop)"
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
      p/Handler
      (handle [this event]
        (case (:zeno/event event)
          :zeno.events/new-frame
          (let [delta (:zeno/delta event 0.0)]
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
      p/Drawable
      (draw [this]
        (draw! @a)))))

(defn elapse
  "Returns a new game where secs have passed, runs functions queued with (defer).

  You should call this every frame with the delta-time.

  Responsible for the key :zeno/elapsed in the game.

  Emits n :zeno.events/elapsed events for each discrete state reached as deferred functions are run."
  [g secs]
  (if (<= secs 0.0)
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
              g (assoc g :zeno/elapsed now-elapsed)
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
   (if (<= secs 0.0)
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
  IDeref
  (draw [this] (p/draw @this)))