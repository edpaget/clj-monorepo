(ns bashketball-game-ui.game.board-utils
  "Hex grid utilities for board rendering.

  Provides pixel coordinate conversions and rendering helpers for the
  5x14 axial hex grid. Uses pointy-top orientation. Re-exports some
  functions from bashketball-game.board for convenience."
  (:require
   [bashketball-game.board :as board]))

(def board-width board/width)
(def board-height board/height)
(def hex-size 40)

(def home-hoop [2 0])
(def away-hoop [2 13])

(def valid-position? board/valid-position?)
(def hex-neighbors board/hex-neighbors)
(def hex-distance board/hex-distance)

(defn hex->pixel
  "Converts axial hex [q r] to pixel [x y] coordinates.

  Uses pointy-top orientation with odd-r offset layout. Returns the
  center point of the hex tile."
  [[q r]]
  (let [size hex-size
        x    (+ (* size (Math/sqrt 3) q)
                (* size (Math/sqrt 3) 0.5 (mod r 2)))
        y    (* size 1.5 r)]
    [x y]))

(defn pixel->hex
  "Converts pixel [x y] to nearest axial hex [q r].

  Uses pointy-top orientation with odd-r offset layout."
  [[px py]]
  (let [size hex-size
        r    (/ py (* size 1.5))
        q    (- (/ px (* size (Math/sqrt 3)))
                (* 0.5 (mod (Math/round r) 2)))
        rr   (Math/round r)
        qq   (Math/round q)]
    [(int qq) (int rr)]))

(defn hex-corners
  "Returns the six corner points for a hex at the given pixel center.

  Used for rendering hex polygons in SVG."
  [[cx cy]]
  (for [i    (range 6)
        :let [angle-deg (- (* 60 i) 30)
              angle-rad (* (/ Math/PI 180) angle-deg)]]
    [(+ cx (* hex-size (Math/cos angle-rad)))
     (+ cy (* hex-size (Math/sin angle-rad)))]))

(defn hex-points-str
  "Returns SVG polygon points string for a hex at the given pixel center."
  [center]
  (->> (hex-corners center)
       (map (fn [[x y]] (str x "," y)))
       (clojure.string/join " ")))

(defn terrain-at
  "Returns terrain info for a position.

  Returns map with :terrain (:hoop, :paint, :three-point-line, :court)
  and optional :side (:home or :away) for hoops."
  [[q r]]
  (cond
    (and (= q 2) (= r 0))        {:terrain :hoop :side :home}
    (and (= q 2) (= r 13))       {:terrain :hoop :side :away}
    (or (<= r 2) (>= r 11))      {:terrain :paint :side (if (<= r 2) :home :away)}
    (or (= r 3) (= r 10))        {:terrain :three-point-line :side (if (= r 3) :home :away)}
    :else                        {:terrain :court}))

(defn terrain-side
  "Returns :home or :away for position based on court half, or nil for center."
  [[_q r]]
  (cond
    (<= r 6) :home
    (>= r 7) :away
    :else nil))

(defn board-dimensions
  "Returns the pixel dimensions needed to render the full board.

  Returns [width height] in pixels including padding."
  []
  (let [padding   20
        max-q     (dec board-width)
        max-r     (dec board-height)
        [max-x _] (hex->pixel [max-q max-r])
        [_ max-y] (hex->pixel [0 max-r])
        width     (+ (* 2 padding) max-x (* hex-size (Math/sqrt 3)))
        height    (+ (* 2 padding) max-y hex-size)]
    [(Math/ceil width) (Math/ceil height)]))

(defn all-positions
  "Returns a sequence of all valid board positions."
  []
  (for [q (range board-width)
        r (range board-height)]
    [q r]))
