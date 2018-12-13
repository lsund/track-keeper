(ns helper.db.update
  (:require [clojure.java.jdbc :as j]
            [helper.util :as util]
            [helper.db.core :as db]
            [helper.db.create :as create]
            [helper.db.read :as read]
            [helper.db.delete :as delete]))

(defn- update-on-id [db table update-map id]
  (j/update! db table update-map ["id=?" id]))

(defn increment [db table column id]
  (j/execute! db
              [(str "UPDATE "
                    (name table)
                    " SET "
                    (name column)
                    " = "
                    (name column)
                    " + 1 WHERE id=?") id])
  (create/taskupdate db :incrementaltask id))

(defn toggle-done [db table id]
  (let [was-done? (read/value db table :done id)]
    (update-on-id db (keyword table) {:done (not was-done?)} id)
    (if was-done?
      (delete/done-task-entry db id)
      (create/taskupdate db table id))))

(defn- succ-priority [p]
  (case p
    :low :middle
    :middle :high
    :high :high))

(defn- pred-priority [p]
  (case p
    :low :low
    :middle :low
    :high :middle))

(defn tweak-priority [db table id op]
  (let [update-fn (if (= op :up) succ-priority pred-priority)
        nxt (some-> db
                    (read/value table :priority id)
                    keyword
                    update-fn)]
    (update-on-id db table {:priority (name nxt)} id)))

(defn tweak-sequence [db table id op]
  (let [update-fn (if (= op :up) util/pred util/succ)
        nxt (some-> db
                    (read/value table :sequence id)
                    update-fn)]
    (when (and nxt (< 0 nxt))
      (update-on-id db table {:sequence nxt} id))))
