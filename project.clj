(defproject zeno "0.1.0-SNAPSHOT"
  :description "A REPL friendly game development library for Clojure."
  :url "https://github.com/danstone/zeno"
  :license "MIT"
  :repositories [["sonatype"
                  "https://oss.sonatype.org/content/repositories/releases/"]]
  :dependencies [[org.clojure/clojure "1.10.1"]
                 [com.badlogicgames.gdx/gdx "1.9.10"]
                 [com.badlogicgames.gdx/gdx-backend-lwjgl "1.9.10"]
                 [com.badlogicgames.gdx/gdx-platform "1.9.10"
                  :classifier "natives-desktop"]]
  :repl-options {:init-ns zeno.core}
  :profiles {:dev {:source-paths ["examples"]}})
