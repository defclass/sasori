# sasori/赤砂之蝎/サソリ 
[![Build Status](https://travis-ci.org/defclass/sasori.svg?branch=master)](https://travis-ci.org/defclass/sasori)

Sasori is a lightweight shell command wrapper and composer. This is very early version, please feel free to submit bugs/thoughts/ideas/suggestions/patches etc.

## Features

* Using function to compose shell commands.
* REPL friendly.
* Support customizable template engine. 
* Support executing commands sequentially and parallelly on different nodes.
* Support colorful and real-time logging output. 

## Usage

Add sasori dependence:

[![Clojars Project](https://img.shields.io/clojars/v/defclass/sasori.svg)](https://clojars.org/defclass/sasori)

Examples:

```clojure
(require '[sasori.dsl :as dsl])

(let [cmd (dsl/cmd "mkdir /tmp/abc")]
  (dsl/emit cmd))
;;=> "mkdir /tmp/abc"

(let [cmd (dsl/cmds
            (dsl/cmd "mkdir /tmp/abc" :exit? false)
            (dsl/cmd "pwd"))]
  (dsl/emit cmd))
;;=> "mkdir /tmp/abc ; pwd"

(let [node (dsl/make-node {:host-info {:host "v1"}})
      cmd (dsl/ssh
            (dsl/cmd "mkdir /tmp/abc")
            (dsl/cmd "ls -alh"))]
  (dsl/emit cmd node))
;;=> "ssh v1 'mkdir /tmp/abc && ls -alh'"

;; Exec shell command

(require '[sasori.core :as sasori])

(let [node (dsl/make-node {:host-info {:host "v1"}})
      cmd (dsl/ssh
            (dsl/cmd "mkdir /tmp/a")
            (dsl/cmd "cd /tmp")
            (dsl/cmd "ls -alh"))]
  (-> (dsl/emit cmd node)
      (sasori/sh-string)))
;;=>
;#sasori.exec.Ok{:code 0,
;                :out ["total 40K"
;                      "drwxrwxrwt 59 root     root      12K Jul  4 17:56 ."
;                      "drwxr-xr-x 23 root     root     4.0K Apr 15 23:10 .."
;                      "drwxrwxr-x  2 defclass defclass 4.0K Jul  4 17:56 a"
;                      ]
;                :err nil}
```

A example to deploy a complete elk log service: [elk service deploy](https://github.com/defclass/sasori/blob/master/examples/src/examples/elk/core.clj)

## License

Copyright © 2018 Michael Wong

Distributed under the Eclipse Public License .
