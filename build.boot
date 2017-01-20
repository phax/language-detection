(set-env!
  :project 'kirasystems/langdetect
  :version "4.0.1"
  :description "This is a language detection library implemented in plain Java. (aliases: language identification, language guessing)"
  :url "https://github.com/kirasystems/language-detection"
  
  :repositories [["clojars" {:url "https://clojars.org/repo/"
                             :username (System/getenv "CLOJARS_USER")
                             :password (System/getenv "CLOJARS_PASS")}]])

(task-options!
  install {:file "build/jar/langdetect.jar"
           :pom "build/jar/pom.xml"}
  pom     {:project (get-env :project)
           :version (get-env :version)}
  push    {:file "build/jar/langdetect.jar"
           :pom "build/jar/pom.xml"}
  target  {:dir #{"build/jar"}
           :no-clean true})

(deftask clean 
  "Remove files generated during build"
  []
  (with-pre-wrap fileset
    (dosh "ant" "clean")
    fileset))

(replace-task! [j jar]
  (fn [& xs] 
    (with-pre-wrap fileset
      (dosh "ant" "jar")
      fileset)))

(replace-task! [p pom] 
  (fn [& xs] 
    (comp 
      (apply p xs) 
      (sift :include #{#"pom\.xml"}
            :move {#"META-INF/maven/kirasystems/langdetect/" ""})
      (target))))

(deftask test 
  "Run the project's tests"
  []
  (with-pre-wrap fileset
    (dosh "ant" "test")
    fileset))

(deftask deploy 
  "Build and deploy jar to Clojars"
  []
  (comp 
    (clean)
    (pom)
    (jar)
    (push :repo "clojars")))