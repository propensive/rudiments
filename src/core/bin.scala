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

import scala.quoted.*

object Rudiments:
  def upperBound
      (expr: Expr[Boolean], bound: Expr[Int | Double | Char], strict: Boolean)
      (using Quotes)
      : Expr[Boolean] =
    val errorMessage = msg"this cannot be written as a range expression"
    
    val value = expr match
      case '{($bound: Int) < ($middle: Int)}        => middle
      case '{($bound: Int) <= ($middle: Int)}       => middle
      case '{($bound: Int) < ($middle: Double)}     => middle
      case '{($bound: Int) <= ($middle: Double)}    => middle
      case '{($bound: Int) < ($middle: Char)}       => middle
      case '{($bound: Int) <= ($middle: Char)}      => middle
      case '{($bound: Double) < ($middle: Int)}     => middle
      case '{($bound: Double) <= ($middle: Int)}    => middle
      case '{($bound: Double) < ($middle: Double)}  => middle
      case '{($bound: Double) <= ($middle: Double)} => middle
      case '{($bound: Char) < ($middle: Int)}       => middle
      case '{($bound: Char) <= ($middle: Int)}      => middle
      case '{($bound: Char) < ($middle: Char)}      => middle
      case '{($bound: Char) <= ($middle: Char)}     => middle
      case _                                        => fail(errorMessage)
    
    val expr2: Expr[Boolean] = value match
      case '{$value: Int}    => bound match
        case '{$bound: Int}    => if strict then '{$value < $bound} else '{$value <= $bound}
        case '{$bound: Double} => if strict then '{$value < $bound} else '{$value <= $bound}
        case '{$bound: Char}   => if strict then '{$value < $bound} else '{$value <= $bound}
        case _                 => fail(errorMessage)
      case '{$value: Double} => bound match
        case '{$bound: Int}    => if strict then '{$value < $bound} else '{$value <= $bound}
        case '{$bound: Double} => if strict then '{$value < $bound} else '{$value <= $bound}
        case '{$bound: Char}   => if strict then '{$value < $bound} else '{$value <= $bound}
        case _                 => fail(errorMessage)
      case '{$value: Char}   => bound match
        case '{$bound: Int}    => if strict then '{$value < $bound} else '{$value <= $bound}
        case '{$bound: Double} => if strict then '{$value < $bound} else '{$value <= $bound}
        case '{$bound: Char}   => if strict then '{$value < $bound} else '{$value <= $bound}
        case _                 => fail(errorMessage)
      case _                 => fail(errorMessage)
      
    '{$expr && $expr2}
      
  
  def bin(expr: Expr[StringContext])(using Quotes): Expr[AnyVal] =
    import quotes.reflect.*
    val bits = expr.valueOrAbort.parts.head

    bits.indexWhere { ch => ch != '0' && ch != '1' && ch != ' ' }.match
      case -1  => ()
      
      case idx =>
        val startPos = expr.asTerm.pos
        val pos = Position(startPos.sourceFile, startPos.start + idx, startPos.start + idx + 1)
        fail(msg"a binary value can only contain characters '0' or '1'", pos)
    
    val bits2 = bits.filter(_ != ' ')

    val long: Long = bits2.foldLeft(0L): (acc, next) =>
      (acc << 1) + (if next == '1' then 1 else 0)

    bits2.length match
      case 8  => Expr[Byte](long.toByte)
      case 16 => Expr[Short](long.toShort)
      case 32 => Expr[Int](long.toInt)
      case 64 => Expr[Long](long)
      case _  => fail(msg"a binary literal must be 8, 16, 32 or 64 bits long")

  def hex(expr: Expr[StringContext])(using Quotes): Expr[IArray[Byte]] =
    import quotes.reflect.*
    
    val startPos = expr.asTerm.pos
    val nibbles = expr.valueOrAbort.parts.head
    val nibbles2 = nibbles.map(_.toLower)

    nibbles2.indexWhere: c =>
      !(c >= '0' && c <= '9') && !(c >= 'a' && c <= 'f') && c != ' ' && c != '\n'
    .match
      case -1  => ()
      
      case idx =>
        val pos = Position(startPos.sourceFile, startPos.start + idx, startPos.start + idx + 1)
        fail(msg"${nibbles(idx)} is not a valid hexadecimal character")

    val nibbles3 = nibbles2.filterNot { ch => ch == ' ' || ch == '\n' }

    if nibbles3.length%2 != 0
    then fail(msg"a hexadecimal value must have an even number of digits", Position.ofMacroExpansion)

    val bytes = nibbles3.grouped(2).map(Integer.parseInt(_, 16).toByte).to(List)

    '{IArray.from(${Expr(bytes)})}

extension (inline context: StringContext)
  transparent inline def bin(): AnyVal = ${Rudiments.bin('context)}
  transparent inline def hex(): IArray[Byte] = ${Rudiments.hex('context)}

extension (inline expr: Boolean)
  @targetName("lt")
  inline def <(upperBound: Int | Char | Double): Boolean =
    ${Rudiments.upperBound('expr, 'upperBound, true)}
  
  @targetName("lte")
  inline def <=(upperBound: Int | Char | Double): Boolean =
    ${Rudiments.upperBound('expr, 'upperBound, false)}