(ns sasori.dsl-test
  (:require [clojure.test :refer :all])
  (:require [sasori.dsl :as dsl]))

(deftest test-cmd
  (is (= "mkdir /tmp/abc"
         (let [cmd (dsl/cmd "mkdir /tmp/abc" :exit? false)]
           (dsl/emit cmd))))

  (is (= "mkdir /tmp/abc"
         (let [cmd (dsl/cmd "mkdir /tmp/abc" :exit? true)]
           (dsl/emit cmd)))))

(deftest test-cmds
  (is (= "mkdir /tmp/abc && ls -alh && touch /tmp/abc/touch-file && pwd"
         (let [cmd (dsl/cmds
                     (dsl/cmd "mkdir /tmp/abc" :exit? true)
                     (dsl/cmd "ls -alh")
                     (dsl/cmd "touch /tmp/abc/touch-file")
                     (dsl/cmd "pwd"))]
           (dsl/emit cmd))))

  (is (= "mkdir /tmp/abc && ls -alh && touch /tmp/abc/touch-file && pwd"
         (let [cmd (dsl/cmds
                     (dsl/cmd "mkdir /tmp/abc")
                     (dsl/cmd "ls -alh")
                     (dsl/cmd "touch /tmp/abc/touch-file")
                     (dsl/cmd "pwd" :exit? false))]
           (dsl/emit cmd))))

  (is (= "mkdir /tmp/abc ; ls -alh && touch /tmp/abc/touch-file && pwd"
         (let [cmd (dsl/cmds
                     (dsl/cmd "mkdir /tmp/abc" :exit? false)
                     (dsl/cmd "ls -alh")
                     (dsl/cmd "touch /tmp/abc/touch-file")
                     (dsl/cmd "pwd"))]
           (dsl/emit cmd)))))

(deftest test-ssh
  (is (= "ssh v1 'mkdir /tmp/abc && ls -alh && touch /tmp/abc/touch-file && pwd'"
         (let [node (#'dsl/make-node {:host-info {:host "v1"}})
               cmd (dsl/ssh
                     (dsl/cmd "mkdir /tmp/abc")
                     (dsl/cmd "ls -alh")
                     (dsl/cmd "touch /tmp/abc/touch-file")
                     (dsl/cmd "pwd"))]
           (dsl/emit cmd node))))

  (is (= "ssh v1 'mkdir /tmp/abc && ls -alh && touch /tmp/abc/touch-file && pwd'"
         (let [node (#'dsl/make-node {:host-info {:host "v1"}})
               cmd (dsl/ssh
                     (dsl/cmds
                       (dsl/cmd "mkdir /tmp/abc")
                       (dsl/cmd "ls -alh")
                       (dsl/cmd "touch /tmp/abc/touch-file")
                       (dsl/cmd "pwd")))]
           (dsl/emit cmd node)))))

(deftest test-sudo
  (is (= "ssh v1 'sudo sh -c '\"'\"'mkdir /tmp/abc'\"'\"''"
         (let [node (#'dsl/make-node {:host-info {:host "v1"}})
               cmd (dsl/ssh
                     (dsl/sudo (dsl/cmd "mkdir /tmp/abc")))]
           (dsl/emit cmd node)))))

(deftest test-sync
  (is (= "rsync --rsync-path='`which rsync`' -v --progress -h /local/path v1:/remote/path"
         (let [node (#'dsl/make-node {:host-info {:host "v1"}
                                    :global-opts {:verbose true}})
               cmd (dsl/rsync "/local/path" "/remote/path")]
           (dsl/emit cmd node))))

  (is (= "rsync --rsync-path='`which rsync`' -v -z --progress -h /local/path v1:/remote/path"
         (let [node (#'dsl/make-node {:host-info {:host "v1"}
                                    :global-opts {:verbose true}})
               cmd (dsl/rsync "/local/path" "/remote/path"
                              {:verbose true
                               :compress true
                               :progress true})]
           (dsl/emit cmd node))))

  (is (= "rsync --rsync-path='`which rsync`' -v --progress -h /local/path v1:/remote/path"
         (let [node (#'dsl/make-node {:host-info {:host "v1"}
                                    :global-opts {:verbose true}})
               cmd (dsl/rsync "/local/path" "/remote/path")]
           (dsl/emit cmd node)))))

(deftest test-scp
  (is (= "scp -vv /local/path/file v1:/remote/path"
         (let [node (#'dsl/make-node {:host-info {:host "v1"}
                                    :global-opts {:verbose true}})
               cmd (dsl/scp "/local/path/file" "/remote/path")]
           (dsl/emit cmd node))))

  (is (= "scp -r /local/path/file v1:/remote/path"
         (let [node (#'dsl/make-node {:host-info {:host "v1"}})
               cmd (dsl/scp "/local/path/file" "/remote/path"
                            {:recursive true})]
           (dsl/emit cmd node))))

  (is (= "scp -vv -r /local/path/file v1:/remote/path"
         (let [node (#'dsl/make-node {:host-info {:host "v1"}
                                    :global-opts {:verbose true}})
               cmd (dsl/scp "/local/path/file" "/remote/path"
                            {:recursive true})]
           (dsl/emit cmd node))))

  (is (= "scp -r -P 22 /local/path/file user@host:/remote/path"
         (let [node (#'dsl/make-node {:host-info {:hostname "host"
                                                :username "user"
                                                :port 22}})
               cmd (dsl/scp "/local/path/file" "/remote/path"
                            {:verbose true
                             :recursive true})]
           (dsl/emit cmd node)))))
