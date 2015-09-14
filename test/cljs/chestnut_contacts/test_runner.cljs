(ns chestnut-contacts.test-runner
  (:require
   [cljs.test :refer-macros [run-tests]]
   [chestnut-contacts.core-test]))

(enable-console-print!)

(defn runner []
  (if (cljs.test/successful?
       (run-tests
        'chestnut-contacts.core-test))
    0
    1))
