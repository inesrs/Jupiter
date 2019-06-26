/*
Copyright (C) 2018-2019 Andres Castellanos

This program is free software: you can redistribute it and/or modify
it under the terms of the GNU General Public License as published by
the Free Software Foundation, either version 3 of the License, or
(at your option) any later version.

This program is distributed in the hope that it will be useful,
but WITHOUT ANY WARRANTY; without even the implied warranty of
MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
GNU General Public License for more details.

You should have received a copy of the GNU General Public License
along with this program.  If not, see <https://www.gnu.org/licenses/>
*/

package vsim.riscv.instructions.rtype;

import vsim.utils.Data;


/** RISC-V fsgnjx.s (Floating-point Sign Inject-XOR, Single-Precision) instruction. */
public final class Fsgnjxs extends FRType {

  /** Creates a new fsgnjx.s instruction. */
  public Fsgnjxs() {
    super("fsgnjx.s");
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public int getFunct3() {
    return 0b010;
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public int getFunct7() {
    return 0b0010000;
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public float compute(float rs1, float rs2) {
    int ax = Float.floatToIntBits(rs1);
    int bx = Float.floatToIntBits(rs2);
    int sign = (ax ^ bx) & Data.SIGN_MASK;
    int cx = ax & (Data.EXPONENT_MASK | Data.FRACTION_MASK);
    return Float.intBitsToFloat(sign | cx);
  }

}
