/*
    Rudiments, version [unreleased]. Copyright 2023 Jon Pretty, Propensive OÜ.

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
import fulminate.*

import language.experimental.captureChecking

object Unset:
  override def toString(): String = "[absent]"

type Maybe[ValueType] = Unset.type | ValueType

case class UnsetValueError() extends Error(Message("the value was not set".tt))

extension [ValueType](maybe: Maybe[ValueType])
  inline def absent: Boolean = maybe == Unset
  inline def present: Boolean = maybe != Unset
  inline def or(inline value: => ValueType): ValueType = if absent then value else maybe.asInstanceOf[ValueType]
  inline def vouch(using Unsafe): ValueType = or(throw Mistake(msg"a value was vouched but was absent"))
  
  def presume(using default: Default[ValueType]): ValueType^{default} = or(default())
  def option: Option[ValueType] = if absent then None else Some(vouch(using Unsafe))
  def assume(using absentValue: CanThrow[UnsetValueError]): ValueType^{absentValue} = or(throw UnsetValueError())
  
  inline def fm[ValueType2](inline alternative: => ValueType2)(inline fn: ValueType => ValueType2): ValueType2 =
    if absent then alternative else fn(vouch(using Unsafe))

  inline def let[ValueType2](inline fn: ValueType => ValueType2): Maybe[ValueType2] =
    if absent then Unset else fn(vouch(using Unsafe))

extension [ValueType](iterable: Iterable[Maybe[ValueType]])
  transparent inline def vouched: Iterable[ValueType] = iterable.filter(!_.absent).map(_.vouch(using Unsafe))

object Maybe:
  inline def apply[ValueType](value: ValueType | Null): Maybe[ValueType] =
    if value == null then Unset else value

extension [ValueType](option: Option[ValueType])
  inline def maybe: Unset.type | ValueType = option.getOrElse(Unset)
  def presume(using default: Default[ValueType]) = option.getOrElse(default())
