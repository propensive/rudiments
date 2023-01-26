/*
    Rudiments, version 0.4.0. Copyright 2020-23 Jon Pretty, Propensive OÜ.

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

import anticipation.*

import scala.collection.IterableFactory
import scala.compiletime.*, ops.int.*

import java.util.regex.*
import java.io as ji
import java.util as ju
import java.util.concurrent as juc
import java.nio.charset as jnc

import scala.util.CommandLineParser

import language.dynamics

import scala.util.{Try, Success, Failure}
import scala.deriving.*
import scala.quoted.*

export scala.util.chaining.scalaUtilChainingOps

export scala.reflect.{ClassTag, Typeable}
export scala.collection.immutable.{Set, List, ListMap, Map, TreeSet, TreeMap}

export Predef.{nn, genericArrayOps, identity, summon, charWrapper, $conforms, ArrowAssoc,
    intWrapper, longWrapper, shortWrapper, byteWrapper, valueOf, doubleWrapper, floatWrapper,
    classOf, locally}

export scala.util.control.NonFatal

export scala.jdk.CollectionConverters.{IteratorHasAsScala, ListHasAsScala, MapHasAsScala, SeqHasAsJava,
    MapHasAsJava, EnumerationHasAsScala}

export scala.annotation.{tailrec, implicitNotFound, targetName, switch, StaticAnnotation}

import language.experimental.captureChecking

type Bytes = IArray[Byte]

opaque type Text = String

object Text:
  def apply(string: String): Text = string
  extension (string: Text) def s: String = string

  given CommandLineParser.FromString[Text] = identity(_)
  given Ordering[Text] = Ordering.String.on[Text](_.s)
  given GenericHttpRequestParam[String, Text] = _.s

  given (using fromExpr: FromExpr[String]): FromExpr[Text] with
    def unapply(expr: Expr[Text])(using Quotes): Option[Text] = fromExpr.unapply(expr).map(Text(_))
  
  given (using toExpr: ToExpr[String]): ToExpr[Text] with
    def apply(txt: Text)(using Quotes): Expr[Text] = toExpr(txt.s)

  given Conversion[String, Text] = Text(_)

  given typeTest: Typeable[Text] with
    def unapply(value: Any): Option[value.type & Text] = value match
      case str: String => Some(str.asInstanceOf[value.type & Text])
      case _           => None

object Bytes:
  def apply(xs: Byte*): Bytes = IArray(xs*)
  def apply(long: Long): Bytes = IArray((56 to 0 by -8).map(long >> _).map(_.toByte)*)
  def empty: Bytes = IArray()

extension [T](value: T)
  def only[S](pf: PartialFunction[T, S]): Option[S] = Some(value).collect(pf)
  def unit: Unit = ()
  def waive: Any => T = _ => value
  def twin: (T, T) = (value, value)
  def triple: (T, T, T) = (value, value, value)
  def puncture(point: T): Maybe[T] = if value == point then Unset else point
  inline def is[S <: T]: Boolean = value.isInstanceOf[S]

  transparent inline def matchable(using erased Unsafe.type): T & Matchable =
    value.asInstanceOf[T & Matchable]

extension [T](value: IArray[T])
  transparent inline def mutable(using erased Unsafe.type): Array[T] = value match
    case array: Array[T] @unchecked => array
    case _                          => throw Mistake("Should never match")

extension [T](value: Array[T])
  transparent inline def immutable(using erased Unsafe.type): IArray[T] = value match
    case array: IArray[T] @unchecked => array
    case _                           => throw Mistake("Should never match")

  def snapshot(using ClassTag[T]): IArray[T] =
    val newArray = new Array[T](value.length)
    System.arraycopy(value, 0, newArray, 0, value.length)
    newArray.immutable(using Unsafe)

extension [K, V](map: Map[K, V])
  def upsert(key: K, op: Maybe[V] => V) = map.updated(key, op(if map.contains(key) then map(key) else Unset))

  def collate(otherMap: Map[K, V])(merge: (V, V) => V): Map[K, V] =
    otherMap.foldLeft(map): (acc, kv) =>
      acc.updated(kv(0), acc.get(kv(0)).fold(kv(1))(merge(kv(1), _)))

extension [K, V](map: Map[K, List[V]])
  def plus(key: K, value: V): Map[K, List[V]] = map.updated(key, map.get(key).fold(List(value))(value :: _))

class Recur[T](fn: => T => T):
  def apply(value: T): T = fn(value)

def fix[T](func: Recur[T] ?-> (T => T)): (T => T) = func(using Recur(fix(func)))
def recur[T: Recur](value: T): T = summon[Recur[T]](value)

case class Property(name: Text) extends Dynamic:
  def apply(): Text throws KeyNotFoundError =
    Text(Option(System.getProperty(name.s)).getOrElse(throw KeyNotFoundError(name)).nn)

  def update(value: Text): Unit = System.setProperty(name.s, value.s)
  def selectDynamic(key: String): Property = Property(Text(s"$name.$key"))
  def applyDynamic(key: String)(): Text throws KeyNotFoundError = selectDynamic(key).apply()

object Sys extends Dynamic:
  def selectDynamic(key: String): Property = Property(Text(key))
  def applyDynamic(key: String)(): Text throws KeyNotFoundError = selectDynamic(key).apply()
  def bigEndian: Boolean = java.nio.ByteOrder.nativeOrder == java.nio.ByteOrder.BIG_ENDIAN

case class KeyNotFoundError(name: Text)
extends Error(ErrorMessage[Text *: EmptyTuple](List(Text("key "), Text(" not found")), name *: EmptyTuple))

extension (iarray: IArray.type)
  def create[T: ClassTag](size: Int)(fn: Array[T] => Unit): IArray[T] =
    val array = new Array[T](size)
    fn(array)
    array.immutable(using Unsafe)

case class Counter(first: Int = 0):
  private var id: Int = first
  def apply(): Int = synchronized(id.tap((id += 1).waive))

package characterEncodings:
  given utf8: Encoding = new Encoding(Text("UTF-8")):
    override def carry(arr: Array[Byte]): Int =
      val len = arr.length
      def last = arr(len - 1)
      def last2 = arr(len - 2)
      def last3 = arr(len - 3)
      
      if len > 0 && ((last & -32) == -64 || (last & -16) == -32 || (last & -8) == -16) then 1
      else if len > 1 && ((last2 & -16) == -32 || (last2 & -8) == -16) then 2
      else if len > 2 && ((last3 & -8) == -16) then 3
      else 0
    
    override def run(byte: Byte): Int =
      if (byte & -32) == -64 then 2 else if (byte & -16) == -32 then 3 else if (byte & -8) == -16 then 4 else 1
    
  given ascii: Encoding = Encoding(Text("US-ASCII"))
  given iso88591: Encoding = Encoding(Text("ISO-8859-1"))

object Encoding:
  import scala.jdk.CollectionConverters.SetHasAsScala

  val all: Set[Text] =
    jnc.Charset.availableCharsets.nn.asScala.to(Map).values.to(Set).flatMap: cs =>
      cs.aliases.nn.asScala.to(Set) + cs.displayName.nn
    .map(_.toLowerCase.nn).map(Text(_))
  
  def unapply(name: Text): Option[Encoding] =
    if !all.contains(Text(name.s.toLowerCase.nn)) then None else Some:
      val charset = jnc.Charset.forName(name.s).nn.displayName.nn
      if charset == "UTF-8" then characterEncodings.utf8 else Encoding(Text(charset))

case class Encoding(name: Text):
  def carry(array: Array[Byte]): Int = 0
  def run(byte: Byte): Int = 1

object AndExtractor:
  @targetName("And")
  object `&`:
    def unapply[T](value: T): Some[(T, T)] = Some((value, value))

export AndExtractor.&

extension (xs: Iterable[Text])
  transparent inline def ss: Iterable[String] = xs

extension [T](xs: Iterable[T])
  transparent inline def mtwin: Iterable[(T, T)] = xs.map { x => (x, x) }
  transparent inline def mtriple: Iterable[(T, T, T)] = xs.map { x => (x, x, x) }
  transparent inline def sift[S]: Iterable[S] = xs.collect { case x: S @unchecked => x }

  def indexBy[S](fn: T -> S): Map[S, T] throws DuplicateIndexError =
    val map = xs.map: value =>
      (fn(value), value)
    
    if xs.size != map.size then throw DuplicateIndexError() else map.to(Map)

object Timer extends ju.Timer(true)

case class DuplicateIndexError()
extends Error(ErrorMessage[EmptyTuple](
  List(Text("the sequence contained more than one element that mapped to the same index")), EmptyTuple
))

//case class TimeoutError() extends Error(err"an operation did not complete in the time it was given")

extension[T](xs: Seq[T])
  def random: T = xs(util.Random().nextInt(xs.length))
  transparent inline def shuffle: Seq[T] = util.Random().shuffle(xs)

extension (bs: Int)
  def b: ByteSize = bs
  def kb: ByteSize = bs*1024L
  def mb: ByteSize = bs*1024L*1024
  def gb: ByteSize = bs*1024L*1024*1024
  def tb: ByteSize = bs*1024L*1024*1024*1024

extension (bs: Long)
  def b: ByteSize = bs
  def kb: ByteSize = bs*1024
  def mb: ByteSize = bs*1024*1024
  def gb: ByteSize = bs*1024*1024*1024
  def tb: ByteSize = bs*1024*1024*1024*1024

opaque type ByteSize = Long

object ByteSize:
  given GenericHttpRequestParam["content-length", ByteSize] = _.long.toString
  given Ordering[ByteSize] = Ordering.Long.on(_.long)

  extension (bs: ByteSize)
    def long: Long = bs

    @targetName("plus")
    infix def +(that: ByteSize): ByteSize = bs + that

    @targetName("gt")
    infix def >(that: ByteSize): Boolean = bs > that

    @targetName("lt")
    infix def <(that: ByteSize): Boolean = bs < that

    @targetName("lte")
    infix def <=(that: ByteSize): Boolean = bs <= that

    @targetName("gte")
    infix def >=(that: ByteSize): Boolean = bs >= that

    @targetName("minus")
    infix def -(that: ByteSize): ByteSize = bs - that

    @targetName("times")
    infix def *(that: Int): ByteSize = bs*that

    @targetName("div")
    infix def /(that: Int): ByteSize = bs/that

object ExitStatus:
  def apply(value: Int): ExitStatus = if value == 0 then Ok else Fail(value)

enum ExitStatus:
  case Ok
  case Fail(status: Int)

  def apply(): Int = this match
    case Ok           => 0
    case Fail(status) => status

case class Pid(value: Long):
  override def toString(): String = "ᴾᴵᴰ｢"+value+"｣"

object Uuid:
  def unapply(text: Text): Option[Uuid] =
    try Some:
      val uuid = ju.UUID.fromString(text.s).nn
      Uuid(uuid.getMostSignificantBits, uuid.getLeastSignificantBits)
    catch case err: Exception => None

  def apply(): Uuid =
    val uuid = ju.UUID.randomUUID().nn
    Uuid(uuid.getMostSignificantBits, uuid.getLeastSignificantBits)

case class Uuid(msb: Long, lsb: Long):
  def javaUuid: ju.UUID = ju.UUID(msb, lsb)
  def bytes: Bytes = Bytes(msb) ++ Bytes(lsb)

inline def env(using env: Environment): Environment = env

package environments:
  given system: Environment(
    v => Option(System.getenv(v.s)).map(_.nn).map(Text(_)),
    v => Option(System.getProperty(v.s)).map(_.nn).map(Text(_))
  )

  given restricted: Environment(
    v => None,
    v => Option(System.getProperty(v.s)).map(_.nn).map(Text(_))
  )

  given empty: Environment(v => None, v => None)

sealed class Internet()

def internet[T](fn: Internet ?=> T): T =
  val inet: Internet = Internet()
  fn(using inet)

extension [P <: Product](product: P)(using mirror: Mirror.ProductOf[P])
  def tuple: mirror.MirroredElemTypes = Tuple.fromProductTyped(product)

extension [T <: Tuple](tuple: T)
  def to[P](using mirror: Mirror.ProductOf[P]): P = mirror.fromProduct(tuple)

object Unsafe

object Default:
  given Default[Int](0)
  given Default[Long](0L)
  given Default[Text](Text(""))
  given Default[String]("")
  given [T]: Default[List[T]](Nil)
  given [T]: Default[Set[T]](Set())
  given [T]: Default[Vector[T]](Vector())

trait Default[+T](default: T):
  def apply(): T = default
