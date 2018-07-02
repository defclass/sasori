# sasori/赤砂之蝎/サソリ 

Wrap and compose shell commands by clojure.

<img src="https://github.com/defclass/sasori/blob/master/assets/sasori.jpg" width="600">

## Features

* 可灵活地组合shell命令.
* REPL友好, 调试方便.
* 简单函数组合, 高度可扩展.
* 支持灵活的模板方案.
* 支持灵活的配置方案.
* 支持多节点顺序和并行部署.
* 支持彩色输出.
* 支持实时log输出(方便hung时判断程序的状态), 是的, 并行时也支持. 

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
