/*
Copyright (C) 2018 Andres Castellanos

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

package vsim.linker;

import vsim.Errors;
import java.io.File;
import vsim.Globals;
import vsim.Settings;
import vsim.utils.Data;
import java.util.HashMap;
import vsim.utils.Message;
import java.io.FileWriter;
import java.util.ArrayList;
import java.io.BufferedWriter;
import vsim.assembler.Program;
import vsim.assembler.Segment;
import vsim.assembler.DebugInfo;
import vsim.riscv.MemorySegments;
import vsim.assembler.statements.UType;
import vsim.assembler.statements.IType;
import vsim.assembler.statements.Statement;
import vsim.riscv.instructions.Instruction;
import vsim.riscv.instructions.InstructionField;


/**
 * The Linker class contains useful methods to generate a RISC-V linked program.
 */
public final class Linker {

  /** where static data segment begins */
  private static int dataAddress = MemorySegments.STATIC_SEGMENT;
  /** where text segment starts */
  private static int textAddress = MemorySegments.TEXT_SEGMENT_BEGIN;

  /**
   * This method takes an array of RISC-V programs and stores
   * all the read-only segment data of these programs in memory.
   *
   * @param programs an array of programs
   * @see vsim.assembler.Program
   */
  private static void linkRodata(ArrayList<Program> programs) {
    int startAddress = Linker.dataAddress;
    MemorySegments.RODATA_SEGMENT_BEGIN = Linker.dataAddress;
    MemorySegments.RODATA_SEGMENT_END = Linker.dataAddress;
    for (Program program: programs) {
      program.setRodataStart(Linker.dataAddress);
      // store every byte of rodata of the current program
      for (Byte b: program.getRodata())
        Globals.memory.storeByte(Linker.dataAddress++, b);
      // align to a word boundary for next program if necessary
      if (Linker.dataAddress != startAddress) {
        Linker.dataAddress = Data.alignToWordBoundary(Linker.dataAddress);
        startAddress = Linker.dataAddress;
        MemorySegments.RODATA_SEGMENT_END = Linker.dataAddress;
      }
    }
    // move next address by 1 word to set a rodata address range properly
    if (MemorySegments.RODATA_SEGMENT_BEGIN != MemorySegments.RODATA_SEGMENT_END)
      Linker.dataAddress += Data.WORD_LENGTH;
    else {
      MemorySegments.RODATA_SEGMENT_BEGIN = -1;
      MemorySegments.RODATA_SEGMENT_END = -1;
    }
  }

  /**
   * This method takes an array of RISC-V programs and stores
   * all the bss segment data of these programs in memory.
   *
   * @param programs an array of programs
   * @see vsim.assembler.Program
   */
  private static void linkBss(ArrayList<Program> programs) {
    int startAddress = Linker.dataAddress;
    for (Program program: programs) {
      program.setBssStart(Linker.dataAddress);
      for (Byte b: program.getBss())
        Globals.memory.storeByte(Linker.dataAddress++, b);
      if (Linker.dataAddress != startAddress) {
        Linker.dataAddress = Data.alignToWordBoundary(Linker.dataAddress);
        startAddress = Linker.dataAddress;
      }
    }
  }

  /**
   * This method takes an array of RISC-V programs and stores
   * all the data segment data of these programs in memory.
   *
   * @param programs an array of programs
   * @see vsim.assembler.Program
   */
  private static void linkData(ArrayList<Program> programs) {
    int startAddress = Linker.dataAddress;
    for (Program program: programs) {
      program.setDataStart(Linker.dataAddress);
      for (Byte b: program.getData())
        Globals.memory.storeByte(Linker.dataAddress++, b);
      if (Linker.dataAddress != startAddress) {
        Linker.dataAddress = Data.alignToWordBoundary(Linker.dataAddress);
        startAddress = Linker.dataAddress;
      }
    }
    MemorySegments.HEAP_SEGMENT = Linker.dataAddress;
    Globals.regfile.setRegister("gp", MemorySegments.HEAP_SEGMENT);
  }

  /**
   * This methods relocates all the symbols of all programs.
   *
   * @param programs an array of programs
   * @see vsim.assembler.Program
   */
  private static void linkSymbols(ArrayList<Program> programs) {
    // first relocate symbols
    for (Program program: programs) {
      program.setTextStart(Linker.textAddress);
      program.relocateSymbols();
      Linker.textAddress += program.getTextSize();
    }
    // then store references to this symbols
    for (Program program: programs) {
      program.storeRefs();
    }
  }

  /**
   * This method tries to build all statements of all programs,
   * i.e generates machine code.
   *
   * @param programs an array of programs
   * @see vsim.assembler.Program
   * @return a RISC-V linked program
   */
  private static LinkedProgram linkPrograms(ArrayList<Program> programs) {
    // set start of text segment
    Linker.textAddress = MemorySegments.TEXT_SEGMENT_BEGIN;
    HashMap<Integer, Statement> all = new HashMap<Integer, Statement>();
    if (Globals.globl.get(Settings.START) != null &&
        Globals.globl.getSymbol(Settings.START).getSegment() == Segment.TEXT) {
      // far call to start label always the first (two) statements
      DebugInfo debug = new DebugInfo(0, "call " + Settings.START, "start");
      // utype statement (CALL start)
      UType u = new UType("auipc", debug, "x6", new Relocation(Relocation.PCRELHI, Settings.START, debug));
      u.build(Linker.textAddress);
      Globals.memory.privStoreWord(Linker.textAddress, u.result().get(InstructionField.ALL));
      all.put(Linker.textAddress, u);
      // next word align address
      Linker.textAddress += Instruction.LENGTH;
      // itype statement (CALL start)
      IType i = new IType("jalr", debug, "x1", "x6", new Relocation(Relocation.PCRELLO, Settings.START, debug));
      i.build(Linker.textAddress);
      Globals.memory.privStoreWord(Linker.textAddress, i.result().get(InstructionField.ALL));
      all.put(Linker.textAddress, i);
      // next word align address
      Linker.textAddress += Instruction.LENGTH;
      for (Program program: programs) {
        for (Statement stmt: program.getStatements()) {
          // build machine code
          stmt.build(Linker.textAddress);
          // store result in text segment
          int code = stmt.result().get(InstructionField.ALL);
          Globals.memory.privStoreWord(Linker.textAddress, code);
          // add this statement
          all.put(Linker.textAddress, stmt);
          // next word align address
          Linker.textAddress += Instruction.LENGTH;
          if (Linker.textAddress > MemorySegments.TEXT_SEGMENT_END)
            Errors.add("linker: program to large > ~256MiB");
        }
      }
    } else
      Errors.add("linker: no global start label: '" + Settings.START + "' set");
    return new LinkedProgram(all);
  }

  /**
   * This method tries to link all programs, handling all data, relocating
   * all symbols and reporting errors if any.
   *
   * @param programs an array of programs
   * @see vsim.linker.LinkedProgram
   * @see vsim.assembler.Program
   * @return a RISC-V linked program
   */
  public static LinkedProgram link(ArrayList<Program> programs) {
    if (programs != null) {
      // reset this
      Linker.dataAddress = MemorySegments.STATIC_SEGMENT;
      // 2 words added because of the two initial statements representing the far call to START label
      Linker.textAddress = MemorySegments.TEXT_SEGMENT_BEGIN + 2 * Instruction.LENGTH;
      // handle static data
      Linker.linkRodata(programs);
      Linker.linkBss(programs);
      Linker.linkData(programs);
      Linker.linkSymbols(programs);
      // link all statements and get linked program
      LinkedProgram program = Linker.linkPrograms(programs);
      // report errors
      if (!Errors.report()) {
        // dump statements ?
        if (Settings.DUMP != null) {
          File f = new File(Settings.DUMP);
          try {
            BufferedWriter bw = new BufferedWriter(new FileWriter(f));
            for (Program p: programs) {
              boolean filename = true;
              for (Statement stmt: p.getStatements()) {
                // write filename
                if (filename && (programs.size() > 1)) {
                  bw.write(stmt.getDebugInfo().getFilename() + ":");
                  bw.newLine();
                  filename = false;
                }
                // write result in file
                int code = stmt.result().get(InstructionField.ALL);
                String out = String.format("%08x", code);
                bw.write(out);
                bw.newLine();
              }
            }
            bw.close();
          } catch (Exception e) {
            if (!Settings.QUIET)
              Message.warning("the file '" + Settings.DUMP + "' could not be written");
          }
        }
        // clean all
        programs = null;
        System.gc();
        // return linked program, now simulate ?
        return program;
      }
      return null;
    }
    return null;
  }

}
