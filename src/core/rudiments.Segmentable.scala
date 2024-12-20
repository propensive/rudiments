package rudiments

import anticipation.*
import denominative.*

object Segmentable:
  given [ElementType] => IndexedSeq[ElementType] is Segmentable =
    (seq, interval) => seq.slice(interval.start.n0, interval.end.n0)

  given [ElementType] => IArray[ElementType] is Segmentable as iarray =
    (iarray, interval) => iarray.slice(interval.start.n0, interval.end.n0)

  given Text is Segmentable = (text, interval) =>
    text.s.substring(interval.start.n0, interval.end.n0).nn.tt

trait Segmentable:
  type Self
  def segment(entity: Self, interval: Interval): Self
