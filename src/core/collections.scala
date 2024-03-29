/*
    Rudiments, version [unreleased]. Copyright 2024 Jon Pretty, Propensive OÜ.

    The primary distribution site is: https://propensive.com/

    Licensed under the Apache License, Version 2.0 (the "License"); you may not use this
    file except in compliance with the License. You may obtain a copy of the License at

    http://www.apache.org/licenses/LICENSE-2.0

    Unless required by applicable law or agreed to in writing, software distributed under the
    License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
    either express or implied. See the License for the specific language governing permissions
    and limitations under the License.
*/

package rudiments

import vacuous.*

import scala.compiletime.*
import scala.collection as sc

import java.io as ji

import language.experimental.captureChecking

extension [ValueType <: Matchable](iterable: Iterable[ValueType])
  transparent inline def sift[FilterType <: ValueType]: Iterable[FilterType] =
    iterable.collect { case value: FilterType => value }
  
  inline def has(value: ValueType): Boolean = iterable.exists(_ == value)

  inline def where(inline predicate: ValueType => Boolean): Optional[ValueType] =
    iterable.find(predicate).getOrElse(Unset)

  transparent inline def interleave(right: Iterable[ValueType]): Iterable[ValueType] =
    iterable.zip(right).flatMap(Iterable(_, _))

extension [ValueType](iterator: Iterator[ValueType])
  transparent inline def each(predicate: ValueType => Unit): Unit = iterator.foreach(predicate)
  inline def all(predicate: ValueType => Boolean): Boolean = iterator.forall(predicate)

extension [ValueType](iterable: Iterable[ValueType])
  transparent inline def each(lambda: ValueType => Unit): Unit = iterable.foreach(lambda)

  def sumBy[NumberType](lambda: ValueType => NumberType)(using numeric: Numeric[NumberType]): NumberType =
    var count = numeric.zero
    
    iterable.foreach: value =>
      count = numeric.plus(count, lambda(value))
    
    count

  inline def all(predicate: ValueType => Boolean): Boolean = iterable.forall(predicate)
  transparent inline def bi: Iterable[(ValueType, ValueType)] = iterable.map { x => (x, x) }
  transparent inline def tri: Iterable[(ValueType, ValueType, ValueType)] = iterable.map { x => (x, x, x) }
  
  def indexBy[ValueType2](lambda: ValueType -> ValueType2): Map[ValueType2, ValueType] =
    iterable.map: value =>
      (lambda(value), value)
    .to(Map)

  def longestTrain(predicate: ValueType -> Boolean): (Int, Int) =
    @tailrec
    def recur(index: Int, iterable: Iterable[ValueType], bestStart: Int, bestLength: Int, length: Int)
            : (Int, Int) =

      if iterable.isEmpty then (bestStart, bestLength) else
        if predicate(iterable.head) then
          if length >= bestLength then recur(index + 1, iterable.tail, index - length, length + 1, length + 1)
          else recur(index + 1, iterable.tail, bestStart, bestLength, length + 1)
        else recur(index + 1, iterable.tail, bestStart, bestLength, 0)

    recur(0, iterable, 0, 0, 0)

extension [ElemType](value: IArray[ElemType])
  inline def mutable(using Unsafe): Array[ElemType] = (value.asMatchable: @unchecked) match
    case array: Array[ElemType] => array

extension [ElemType](array: Array[ElemType])
  def immutable(using Unsafe): IArray[ElemType] = (array: @unchecked) match
    case array: IArray[ElemType] => array

  def snapshot(using ClassTag[ElemType]): IArray[ElemType] =
    val newArray = new Array[ElemType](array.length)
    System.arraycopy(array, 0, newArray, 0, array.length)
    newArray.immutable(using Unsafe)

  inline def place(value: IArray[ElemType], index: Int = 0): Unit =
    System.arraycopy(value.asInstanceOf[Array[ElemType]], 0, array, index, value.length)

extension [KeyType, ValueType](map: sc.Map[KeyType, ValueType])
  inline def has(key: KeyType): Boolean = map.contains(key)
  
  transparent inline def at(key: KeyType): Optional[ValueType] = optimizable[ValueType]: default =>
    if map.has(key) then map(key) else default

extension [KeyType, ValueType](map: Map[KeyType, ValueType])
  def upsert(key: KeyType, op: Optional[ValueType] => ValueType): Map[KeyType, ValueType] =
    map.updated(key, op(if map.contains(key) then map(key) else Unset))

  def collate(right: Map[KeyType, ValueType])(merge: (ValueType, ValueType) -> ValueType)
          : Map[KeyType, ValueType] =

    right.foldLeft(map): (accumulator, keyValue) =>
      accumulator.updated(keyValue(0), accumulator.get(keyValue(0)).fold(keyValue(1))(merge(_, keyValue(1))))

extension [KeyType, ValueType](map: Map[KeyType, List[ValueType]])
  def plus(key: KeyType, value: ValueType): Map[KeyType, List[ValueType]] =
    map.updated(key, map.get(key).fold(List(value))(value :: _))

extension [ElemType](seq: Seq[ElemType])
  def runs: List[List[ElemType]] = runsBy(identity)

  inline def prim: Optional[ElemType] = if seq.isEmpty then Unset else seq.head
  inline def sec: Optional[ElemType] = if seq.length < 2 then Unset else seq(1)
  inline def ter: Optional[ElemType] = if seq.length < 3 then Unset else seq(2)

  def runsBy(lambda: ElemType => Any): List[List[ElemType]] =
    @tailrec
    def recur(current: Any, todo: Seq[ElemType], run: List[ElemType], done: List[List[ElemType]])
             : List[List[ElemType]] =
      if todo.isEmpty then (run.reverse :: done).reverse
      else
        val focus = lambda(todo.head)
        if current == focus then recur(current, todo.tail, todo.head :: run, done)
        else recur(focus, todo.tail, List(todo.head), run.reverse :: done)

    if seq.isEmpty then Nil else recur(lambda(seq.head), seq.tail, List(seq.head), Nil)

object Cursor:
  opaque type Cursor = Int
  opaque type CursorSeq[T] <: IndexedSeq[T] = IndexedSeq[T]

  extension (cursor: Cursor)
    inline def index: Int = cursor
    
    inline def of[ElemType](inline seq: CursorSeq[ElemType]): ElemType = seq(cursor.index)
    
    inline def of[ElemType](inline seq: CursorSeq[ElemType], inline offset: Int): Optional[ElemType] =
      if (cursor.index + offset) >= 0 && (cursor.index + offset) < seq.length then seq(cursor.index + offset)
      else Unset

  inline def curse[ElemType, ElemType2](seq: IndexedSeq[ElemType])
      (inline block: (CursorSeq[ElemType], Cursor) ?=> ElemType2)
          : IndexedSeq[ElemType2] =

    seq.indices.map { index => block(using seq, index) }

inline def cursor[ElemType](using inline seq: Cursor.CursorSeq[ElemType], inline cursor: Cursor.Cursor)
        : ElemType =

  cursor.of(seq)

inline def precursor[ElemType](using inline seq: Cursor.CursorSeq[ElemType], inline cursor: Cursor.Cursor)
        : Optional[ElemType] =

  cursor.of(seq, -1)

inline def postcursor[ElemType](using inline seq: Cursor.CursorSeq[ElemType], inline cursor: Cursor.Cursor)
        : Optional[ElemType] =

  cursor.of(seq, 1)

inline def cursorIndex(using inline cursor: Cursor.Cursor): Int = cursor.index

inline def cursorOffset[ElemType](offset: Int)
    (using inline seq: Cursor.CursorSeq[ElemType], inline cursor: Cursor.Cursor)
        : Optional[ElemType] =
  cursor.of(seq, offset)

extension [ElemType](seq: IndexedSeq[ElemType])
  transparent inline def curse[ElemType2]
      (inline block: (Cursor.CursorSeq[ElemType], Cursor.Cursor) ?=> ElemType2)
          : IndexedSeq[ElemType2] =
    Cursor.curse(seq)(block)
  
  transparent inline def has(index: Int): Boolean = index >= 0 && index < seq.length
  
  transparent inline def at(index: Int): Optional[ElemType] = optimizable[ElemType]: default =>
    if has(index) then seq(index) else default
  
  inline def ult: Optional[ElemType] = at(seq.length - 1)

extension (iarray: IArray.type)
  def create[ElemType: ClassTag](size: Int)(lambda: Array[ElemType] => Unit): IArray[ElemType] =
    val array: Array[ElemType] = new Array[ElemType](size)
    lambda(array)
    array.immutable(using Unsafe)

extension (bytes: Bytes)
  def javaInputStream: ji.InputStream = new ji.ByteArrayInputStream(bytes.mutable(using Unsafe))
