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

package jupiter.gui.controllers;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Hashtable;

import javafx.application.Platform;
import javafx.beans.binding.Bindings;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.Label;
import javafx.scene.control.MenuItem;
import javafx.scene.control.Tab;
import javafx.scene.control.Tooltip;
import javafx.scene.control.TreeTableColumn;
import javafx.scene.control.TreeTableColumn.CellEditEvent;
import javafx.scene.control.cell.TreeItemPropertyValueFactory;
import javafx.stage.FileChooser.ExtensionFilter;

import com.jfoenix.controls.JFXButton;
import com.jfoenix.controls.JFXComboBox;
import com.jfoenix.controls.JFXTabPane;
import com.jfoenix.controls.JFXTextField;
import com.jfoenix.controls.JFXTreeTableView;
import com.jfoenix.controls.RecursiveTreeItem;
import com.jfoenix.controls.datamodels.treetable.RecursiveTreeObject;

import jupiter.Flags;
import jupiter.Globals;
import jupiter.Logger;
import jupiter.asm.Assembler;
import jupiter.asm.stmts.Statement;
import jupiter.exc.*;
import jupiter.gui.Icons;
import jupiter.gui.Settings;
import jupiter.gui.Status;
import jupiter.gui.components.*;
import jupiter.gui.models.*;
import jupiter.linker.LinkedProgram;
import jupiter.linker.Linker;
import jupiter.riscv.hardware.Cache.ReplacePolicy;
import jupiter.sim.History;
import jupiter.utils.Data;
import jupiter.utils.Dump;
import jupiter.utils.FS;


/** Jupiter GUI simulator controller. */
public final class Simulator {

  /** number of memory cells to show in the GUI application */
  private static final int ROWS = 32;

  private static final SimpleBooleanProperty ASSOC_DISABLE = new SimpleBooleanProperty(true);

  /** start address to generate memory cells */
  private static int START = Data.TEXT + (ROWS - 1) * Data.WORD_LENGTH;

  /** main controller */
  private Main mainController;
  /** current run task */
  private Task<Void> runTask;
  /** current linked program */
  private LinkedProgram program;
  /** list of memory cells */
  private ObservableList<MemoryItem> mlist;
  /** list of RVI registers */
  private ObservableList<RegisterItem> xlist;
  /** list of RVF registers */
  private ObservableList<RegisterItem> flist;
  /** list of statements */
  private ObservableList<StatementItem> tlist;
  /** simulation history */
  private final History history;
  /** list of breakpoints */
  private final Hashtable<Integer, Boolean> breakpoints;

  /** hardware tab pane */
  @FXML private JFXTabPane hardware;

  /** ST tab */
  @FXML private Tab symbolTableTab;

  /** run button */
  @FXML private JFXButton run;
  /** step button */
  @FXML private JFXButton step;
  /** backstep button */
  @FXML private JFXButton backstep;
  /** stop button */
  @FXML private JFXButton stop;
  /** reset button */
  @FXML private JFXButton reset;
  /** dump code button */
  @FXML private JFXButton dumpCode;
  /** dump data button */
  @FXML private JFXButton dumpData;
  /** associativity plus button */
  @FXML private JFXButton blockSizePlusBtn;
  /** associativity minus button */
  @FXML private JFXButton blockSizeMinusBtn;
  /** associativity plus button */
  @FXML private JFXButton numBlocksPlusBtn;
  /** associativity minus button */
  @FXML private JFXButton numBlocksMinusBtn;
  /** associativity plus button */
  @FXML private JFXButton assocPlusBtn;
  /** associativity minus button */
  @FXML private JFXButton assocMinusBtn;

  /** segment combobox */
  @FXML private JFXComboBox<Label> segment;
  /** cache map combobox */
  @FXML private JFXComboBox<Label> cacheMap;
  /** cache policy combobox */
  @FXML private JFXComboBox<Label> cachePolicy;

  /** block size text field */
  @FXML private JFXTextField blockSize;
  /** num blocks text field */
  @FXML private JFXTextField numBlocks;
  /** associativity text fiefld */
  @FXML private JFXTextField assoc;
  /** cache size text field */
  @FXML private JFXTextField cacheSize;
  /** cache size text field */
  @FXML private JFXTextField accesses;
  /** cache size text field */
  @FXML private JFXTextField hits;
  /** cache size text field */
  @FXML private JFXTextField hitRate;

  /** RVI tree table view */
  @FXML private JFXTreeTableView<RegisterItem> rviTable;
  /** RVI mnemonic tree table column */
  @FXML private TreeTableColumn<RegisterItem, String> rviMnemonic;
  /** RVI number tree table column */
  @FXML private TreeTableColumn<RegisterItem, String> rviNumber;
  /** RVI value tree table column */
  @FXML private TreeTableColumn<RegisterItem, String> rviValue;

  /** RVF tree table view */
  @FXML private JFXTreeTableView<RegisterItem> rvfTable;
  /** RVF mnemonic tree table column */
  @FXML private TreeTableColumn<RegisterItem, String> rvfMnemonic;
  /** RVF number tree table column */
  @FXML private TreeTableColumn<RegisterItem, String> rvfNumber;
  /** RVF value tree table column */
  @FXML private TreeTableColumn<RegisterItem, String> rvfValue;

  /** memory tree table view */
  @FXML private JFXTreeTableView<MemoryItem> memoryTable;
  /** memory address tree table column */
  @FXML private TreeTableColumn<MemoryItem, String> memoryAddress;
  /** memory +0 tree table column */
  @FXML private TreeTableColumn<MemoryItem, String> memoryOffset0;
  /** memory +1 tree table column */
  @FXML private TreeTableColumn<MemoryItem, String> memoryOffset1;
  /** memory +2 tree table column */
  @FXML private TreeTableColumn<MemoryItem, String> memoryOffset2;
  /** memory +3 tree table column */
  @FXML private TreeTableColumn<MemoryItem, String> memoryOffset3;

  /** RVF tree table view */
  @FXML private JFXTreeTableView<SymbolItem> symbolTable;
  /** RVF mnemonic tree table column */
  @FXML private TreeTableColumn<SymbolItem, String> symbolTableName;
  /** RVF number tree table column */
  @FXML private TreeTableColumn<SymbolItem, String> symbolTableAddress;

  /** Cache tree table view */
  @FXML private JFXTreeTableView<CacheItem> cacheTable;
  /** RVF mnemonic tree table column */
  @FXML private TreeTableColumn<CacheItem, String> cacheBlocks;

  /** text tree table extension */
  private TextTableExt textExt;
  /** text tree table view */
  @FXML private JFXTreeTableView<StatementItem> textTable;
  /** text bkpt tree table column */
  @FXML private TreeTableColumn<StatementItem, Boolean> textBkpt;
  /** text address tree table column */
  @FXML private TreeTableColumn<StatementItem, String> textAddress;
  /** text code tree table column */
  @FXML private TreeTableColumn<StatementItem, String> textCode;
  /** text basic tree table column */
  @FXML private TreeTableColumn<StatementItem, String> textBasic;
  /** text source tree table column */
  @FXML private TreeTableColumn<StatementItem, String> textSource;

  /** Creates a new Simulator controller. */
  public Simulator() {
    history = new History();
    breakpoints = new Hashtable<>();
  }

  /**
   * Initializes Jupiter's GUI simulator controller.
   *
   * @param mainController main controller
   */
  protected void initialize(Main mainController) {
    this.mainController = mainController;
    initControls();
    Settings.SHOW_ST.addListener((e, o, n) -> symbolTable());
  }

  /** Assembles RISC-V files. */
  protected synchronized void assemble() {
    mainController.assembling(true);
    // save tabs
    Thread th = new Thread(new Task<Void>() {
      /** {@inheritDoc} */
      @Override
      protected Void call() {
        mainController.loading(true);
        // reset simulator state
        breakpoints.clear();
        history.reset();
        Globals.globl.reset();
        Globals.local.clear();
        if (mainController.editorController.allSaved()) {
          try {
            program = Linker.link(Assembler.assemble(mainController.editorController.getFiles()));
            program.load();
            setText();
            setSymbolTable();
            setState();
            refreshSim();
            scroll();
            Status.READY.set(true);
            Platform.runLater(() -> mainController.simulator());
          } catch (AssemblerException e) {
            Logger.error(e.getMessage());
          } catch (LinkerException e) {
            Logger.error(e.getMessage());
          }
        } else {
          Logger.warning("operation cancelled, save all files first");
        }
        mainController.loading(false);
        mainController.assembling(false);
        return null;
      }
    });
    th.setDaemon(true);
    th.start();
  }

  /** Runs currrent RISC-V program. */
  @FXML protected synchronized void run() {
    runTask = new Task<Void>() {
      /** {@inheritDoc} */
      @Override
      protected Void call() {
        Status.RUNNING.set(true);
        mainController.loading(true);
        refreshSim();
        while (!isCancelled()) {
          int pc = program.getState().xregfile().getProgramCounter();
          try {
            Statement stmt = program.next();
            // handle breakpoints
            if (breakpoints.containsKey(pc) && breakpoints.get(pc)) {
              breakpoints.put(pc, false);
              break;
            }
            // save PC and heap pointer
            history.savePCAndHeap(program.getState());
            // execute
            Globals.iset.get(stmt.mnemonic()).execute(stmt.code(), program.getState());
            // save memory and register files
            history.saveMemAndRegs(program.getState());
            // restore breakpoint state
            if (breakpoints.containsKey(pc)) {
              breakpoints.put(pc, true);
            }
          } catch (BreakpointException e) {
            breakpoints.put(pc, true);
          } catch (HaltException e) {
            Status.EXIT.set(true);
            Platform.runLater(() -> mainController.toast(String.format("exit(%d)", e.getCode()), 2500));
            break;
          } catch (SimulationException e) {
            Status.EXIT.set(true);
            Logger.error(e.getMessage());
            Platform.runLater(() -> mainController.toast(String.format("exit(%d)", -1), 2500));
            break;
          }
        }
        Status.RUNNING.set(false);
        Status.EMPTY.set(history.empty());
        mainController.loading(false);
        refreshSim();
        scroll();
        return null;
      }
    };
    Thread th = new Thread(runTask);
    th.setDaemon(true);
    th.start();
  }

  /** Continue until another instruction reached. */
  @FXML protected synchronized void step() {
    Thread th = new Thread(new Task<Void>() {
      /** {@inheritDoc} */
      @Override
      public Void call() {
        Status.RUNNING.set(true);
        mainController.loading(true);
        try {
          // get next statement
          Statement stmt = program.next();
          // save PC and heap pointer
          history.savePCAndHeap(program.getState());
          // execute instruction
          Globals.iset.get(stmt.mnemonic()).execute(stmt.code(), program.getState());
          // save memory and register files
          history.saveMemAndRegs(program.getState());
        } catch (BreakpointException e) {
          // nothing here :]
        } catch (HaltException e) {
          Status.EXIT.set(true);
          Platform.runLater(() -> mainController.toast(String.format("exit(%d)", e.getCode()), 2500));
        } catch (SimulationException e) {
          Status.EXIT.set(true);
          Logger.error(e.getMessage());
          Platform.runLater(() -> mainController.toast(String.format("exit(%d)", -1), 2500));
        }
        Status.RUNNING.set(false);
        Status.EMPTY.set(history.empty());
        mainController.loading(false);
        refreshSim();
        scroll();
        return null;
      }
    });
    th.setDaemon(true);
    th.start();
  }

  /** Back to the previous instruction */
  @FXML protected synchronized void backstep() {
    Thread th = new Thread(new Task<Void>() {
      /** {@inheritDoc} */
      @Override
      public Void call() {
        if (!history.empty()) {
          history.restore(program.getState());
          Status.EMPTY.set(history.empty());
          refreshSim();
          scroll();
        }
        return null;
      }
    });
    th.setDaemon(true);
    th.start();
  }

  /** Stops simulation. */
  @FXML protected synchronized void stop() {
    if (runTask != null) {
      runTask.cancel();
      runTask = null;
    }
  }

  /** Resets simulation. */
  @FXML protected synchronized void reset() {
    Thread th = new Thread(new Task<Void>() {
      /** {@inheritDoc} */
      @Override
      public Void call() {
        history.reset();
        program.getState().reset();
        program.load();
        updateMemoryCells();
        updateCacheOrganization();
        refreshSim();
        scroll();
        Status.EXIT.set(false);
        Status.EMPTY.set(true);
        return null;
      }
    });
    th.setDaemon(true);
    th.start();
  }

  /** Dumps generated machine code. */
  @FXML private void dumpCode() {
    ExtensionFilter filter = new ExtensionFilter("All Files", "*");
    File file = mainController.fileDialog().save("Dump Machine Code", "code.txt", filter);
    if (file != null) {
      try {
        Dump.dumpCode(file, program);
        Logger.info("machine code dumped to file: " + file);
      } catch (IOException e) {
        Logger.warning("could not dump machine code to file: " + file);
      }
    }
  }

  /** Dumps static data. */
  @FXML private void dumpData() {
    ExtensionFilter filter = new ExtensionFilter("All Files", "*");
    File file = mainController.fileDialog().save("Dump Static Data", "data.txt", filter);
    if (file != null) {
      try {
        Dump.dumpData(file, program);
        Logger.info("static data dumped to file: " + file);
      } catch (IOException e) {
        Logger.warning("could not dump static data to file: " + file);
      }
    }
  }

  /** Clears all breakpoints. */
  protected synchronized void clearAllBreakpoints() {
    breakpoints.clear();
    // restore ebreak instructions
    for (Integer addr : program.breaks()) {
      breakpoints.put(addr, true);
    }
    for (StatementItem item : tlist) {
      if (!item.isEbreak()) {
        item.bkptProperty().set(false);
      }
    }
    textTable.refresh();
  }

  /** Memory up action. */
  @FXML private synchronized void up() {
    START += 16;
    updateMemoryCells();
  }

  /** Memory down action. */
  @FXML private synchronized void down() {
    START -= 16;
    updateMemoryCells();
  }

  /** Increments cache block size. */
  @FXML private synchronized void blockSizePlus() {
    Flags.CACHE_BLOCK_SIZE *= 2;
    program.getState().memory().cache().setBlockSize(Flags.CACHE_BLOCK_SIZE);
    updateCacheOrganization();
  }

  /** Decrements cache block size. */
  @FXML private synchronized void blockSizeMinus() {
    Flags.CACHE_BLOCK_SIZE /= 2;
    Flags.CACHE_BLOCK_SIZE = Math.max(Flags.CACHE_BLOCK_SIZE, 1);
    program.getState().memory().cache().setBlockSize(Flags.CACHE_BLOCK_SIZE);
    updateCacheOrganization();
  }

  /** Increments number of cache blocks. */
  @FXML private synchronized void numBlocksPlus() {
    Flags.CACHE_NUM_BLOCKS *= 2;
    program.getState().memory().cache().setNumBlocks(Flags.CACHE_NUM_BLOCKS);
    updateCacheOrganization();
  }

  /** Decrements number of cache blocks. */
  @FXML private synchronized void numBlocksMinus() {
    Flags.CACHE_NUM_BLOCKS /= 2;
    Flags.CACHE_NUM_BLOCKS = Math.max(Flags.CACHE_NUM_BLOCKS, 1);
    program.getState().memory().cache().setNumBlocks(Flags.CACHE_NUM_BLOCKS);
    updateCacheOrganization();
  }

  /** Increments cache associativity. */
  @FXML private synchronized void assocPlus() {
    Flags.CACHE_ASSOCIATIVITY *= 2;
    program.getState().memory().cache().setAssociativity(Flags.CACHE_ASSOCIATIVITY);
    Flags.CACHE_ASSOCIATIVITY = program.getState().memory().cache().getAssociativity();
    updateCacheOrganization();
  }

  /** Decrements cache associativity. */
  @FXML private synchronized void assocMinus() {
    Flags.CACHE_ASSOCIATIVITY /= 2;
    Flags.CACHE_ASSOCIATIVITY = Math.max(Flags.CACHE_ASSOCIATIVITY, 1);
    program.getState().memory().cache().setAssociativity(Flags.CACHE_ASSOCIATIVITY);
    updateCacheOrganization();
  }

  /** Initializes tree table views. */
  private void initControls() {
    // rvi
    rviTable.setRowFactory(p -> new RegFileRow());
    rviMnemonic.setCellValueFactory(new TreeItemPropertyValueFactory<>("mnemonic"));
    rviNumber.setCellValueFactory(new TreeItemPropertyValueFactory<>("number"));
    rviValue.setCellValueFactory(new TreeItemPropertyValueFactory<>("value"));
    rviMnemonic.setCellFactory(p -> new DisplayCell<>());
    rviNumber.setCellFactory(p -> new DisplayCell<>());
    rviValue.setCellFactory(p -> new EditableCell<>(program.getState()));
    rviValue.setOnEditCommit(e -> updateXReg(e));
    // rvf
    rvfTable.setRowFactory(p -> new RegFileRow());
    rvfMnemonic.setCellValueFactory(new TreeItemPropertyValueFactory<>("mnemonic"));
    rvfNumber.setCellValueFactory(new TreeItemPropertyValueFactory<>("number"));
    rvfValue.setCellValueFactory(new TreeItemPropertyValueFactory<>("value"));
    rvfMnemonic.setCellFactory(p -> new DisplayCell<>());
    rvfNumber.setCellFactory(p -> new DisplayCell<>());
    rvfValue.setCellFactory(p -> new EditableCell<>(program.getState()));
    rvfValue.setOnEditCommit(e -> updateFReg(e));
    // memory
    memoryTable.setRowFactory(p -> new MemoryRow());
    memoryAddress.setCellValueFactory(new TreeItemPropertyValueFactory<>("address"));
    memoryOffset0.setCellValueFactory(new TreeItemPropertyValueFactory<>("byte0"));
    memoryOffset1.setCellValueFactory(new TreeItemPropertyValueFactory<>("byte1"));
    memoryOffset2.setCellValueFactory(new TreeItemPropertyValueFactory<>("byte2"));
    memoryOffset3.setCellValueFactory(new TreeItemPropertyValueFactory<>("byte3"));
    memoryOffset0.setCellFactory(p -> new EditableCell<>(program.getState()));
    memoryOffset1.setCellFactory(p -> new EditableCell<>(program.getState()));
    memoryOffset2.setCellFactory(p -> new EditableCell<>(program.getState()));
    memoryOffset3.setCellFactory(p -> new EditableCell<>(program.getState()));
    memoryOffset0.setOnEditCommit(e -> updateMemory(e, 0));
    memoryOffset1.setOnEditCommit(e -> updateMemory(e, 1));
    memoryOffset2.setOnEditCommit(e -> updateMemory(e, 2));
    memoryOffset3.setOnEditCommit(e -> updateMemory(e, 3));
    // cache table
    cacheTable.setRowFactory(p -> new CacheRow());
    cacheBlocks.setCellValueFactory(new TreeItemPropertyValueFactory<>("state"));
    cacheBlocks.setCellFactory(p -> new DisplayCell<>());
    // symbol table
    memoryTable.setRowFactory(p -> new MemoryRow());
    symbolTableName.setCellValueFactory(new TreeItemPropertyValueFactory<>("name"));
    symbolTableAddress.setCellValueFactory(new TreeItemPropertyValueFactory<>("address"));
    symbolTableName.setCellFactory(p -> new DisplayCell<>());
    symbolTableAddress.setCellFactory(p -> new DisplayCell<>());
    symbolTable();
    // text
    textTable.setRowFactory(p -> new TextRow(program.getState()));
    textBkpt.setCellValueFactory(new TreeItemPropertyValueFactory<>("bkpt"));
    textAddress.setCellValueFactory(new TreeItemPropertyValueFactory<>("address"));
    textCode.setCellValueFactory(new TreeItemPropertyValueFactory<>("code"));
    textBasic.setCellValueFactory(new TreeItemPropertyValueFactory<>("basic"));
    textSource.setCellValueFactory(new TreeItemPropertyValueFactory<>("source"));
    textBkpt.setCellFactory(p -> new TextBooleanCell());
    textAddress.setCellFactory(p -> new DisplayCell<>());
    textCode.setCellFactory(p -> new DisplayCell<>());
    textBasic.setCellFactory(p -> new DisplayCell<>());
    textSource.setCellFactory(p -> new DisplayCell<>());
    // buttons
    run.disableProperty().bind(Bindings.or(Status.RUNNING, Status.EXIT));
    step.disableProperty().bind(Bindings.or(Status.RUNNING, Status.EXIT));
    backstep.disableProperty().bind(Bindings.or(Status.EMPTY, Bindings.or(Status.RUNNING, Status.EXIT)));
    stop.disableProperty().bind(Bindings.or(Bindings.not(Status.RUNNING), Status.EXIT));
    reset.disableProperty().bind(Status.EMPTY);
    run.setTooltip(new Tooltip("Run"));
    step.setTooltip(new Tooltip("Step"));
    backstep.setTooltip(new Tooltip("Backstep"));
    stop.setTooltip(new Tooltip("Stop"));
    reset.setTooltip(new Tooltip("Reset"));
    dumpCode.setTooltip(new Tooltip("Dump Code"));
    dumpData.setTooltip(new Tooltip("Dump Data"));
    blockSizePlusBtn.disableProperty().bind(Bindings.not(Status.EMPTY));
    blockSizeMinusBtn.disableProperty().bind(Bindings.not(Status.EMPTY));
    numBlocksPlusBtn.disableProperty().bind(Bindings.not(Status.EMPTY));
    numBlocksMinusBtn.disableProperty().bind(Bindings.not(Status.EMPTY));
    assocPlusBtn.disableProperty().bind(Bindings.or(ASSOC_DISABLE, Bindings.not(Status.EMPTY)));
    assocMinusBtn.disableProperty().bind(Bindings.or(ASSOC_DISABLE, Bindings.not(Status.EMPTY)));
    // cache combos
    cacheMap.getItems().addAll(new Label("Direct Mapped"), new Label("N-Way Set Assoc"), new Label("Fully Assoc"));
    cacheMap.getSelectionModel().select(0);
    cacheMap.getSelectionModel().selectedItemProperty().addListener((e, o, n) -> setCacheMap(n.getText()));
    cacheMap.disableProperty().bind(Bindings.not(Status.EMPTY));
    cachePolicy.getItems().addAll(new Label("LRU"), new Label("FIFO"), new Label("RAND"));
    cachePolicy.getSelectionModel().select(0);
    cachePolicy.getSelectionModel().selectedItemProperty().addListener((e, o, n) -> setCachePolicy(n.getText()));
    cachePolicy.disableProperty().bind(Bindings.not(Status.EMPTY));
    // memory combo box
    segment.getItems().addAll(new Label("text"), new Label("data"), new Label("stack"), new Label("heap"));
    segment.getSelectionModel().select(0);
    segment.getSelectionModel().selectedItemProperty().addListener((e, o, n) -> setSegment(n.getText()));
    // set rvi table context menu
    MenuItem hex = new MenuItem("Hex Display Mode");
    hex.setGraphic(Icons.get("hex"));
    hex.setOnAction(e -> updateRVIDisplayMode(RegisterItem.HEX));
    MenuItem decimal = new MenuItem("Decimal Display Mode");
    decimal.setGraphic(Icons.get("decimal"));
    decimal.setOnAction(e -> updateRVIDisplayMode(RegisterItem.DEC));
    MenuItem unsigned = new MenuItem("Unsigned Display Mode");
    unsigned.setGraphic(Icons.get("unsigned"));
    unsigned.setOnAction(e -> updateRVIDisplayMode(RegisterItem.UNS));
    ContextMenu menu = new ContextMenu();
    menu.getItems().addAll(hex, decimal, unsigned);
    rviTable.setContextMenu(menu);
    // set rvf table context menu
    hex = new MenuItem("Hex Display Mode");
    hex.setGraphic(Icons.get("hex"));
    hex.setOnAction(e -> updateRVFDisplayMode(RegisterItem.HEX));
    MenuItem dfloat = new MenuItem("Float Display Mode");
    dfloat.setGraphic(Icons.get("float"));
    dfloat.setOnAction(e -> updateRVFDisplayMode(RegisterItem.FLT));
    menu = new ContextMenu();
    menu.getItems().addAll(hex, dfloat);
    rvfTable.setContextMenu(menu);
    // set memory table context menu
    MenuItem ascii = new MenuItem("ASCII Display Mode");
    ascii.setGraphic(Icons.get("ascii"));
    ascii.setOnAction(e -> updateMemoryDisplayMode(MemoryItem.ASC));
    hex = new MenuItem("Hex Display Mode");
    hex.setGraphic(Icons.get("hex"));
    hex.setOnAction(e -> updateMemoryDisplayMode(MemoryItem.HEX));
    decimal = new MenuItem("Decimal Display Mode");
    decimal.setGraphic(Icons.get("decimal"));
    decimal.setOnAction(e -> updateMemoryDisplayMode(MemoryItem.DEC));
    menu = new ContextMenu();
    menu.getItems().addAll(hex, decimal, ascii);
    memoryTable.setContextMenu(menu);
    // set text extension
    textExt = new TextTableExt(textTable);
  }

  /** Sets simulator text table. */
  private void setText() {
    tlist = FXCollections.observableArrayList();
    int pc = Data.TEXT;
    for (Statement stmt : program.statements()) {
      int code = stmt.code().bits();
      String basic = Globals.iset.get(stmt.mnemonic()).disassemble(stmt.code());
      // get source line
      String source = null;
      File file = stmt.getFile();
      int line = stmt.getLine();
      if (line > 0) {
        try {
          // get source from file
          source = FS.getLine(file, line);
          // use basic code if necessary
          source = (source == null) ? basic : source;
          // remove comments
          source = source.replaceAll("[;#].*", "");
          // normalize whitespace (tabs, spaces)
          source = source.replaceAll("( |\t)+", " ");
          // normalize commas
          source = source.replaceAll("( )?,( )?", ", ");
          // remove labels
          source = source.replaceAll("[a-zA-Z_]([a-zA-Z0-9_]*(\\.[a-zA-Z0-9_]+)?):", "");
          // trim whitespace
          source = source.trim();
        } catch (IOException e) {
          source = basic;
        }
      } else {
        source = basic;
      }
      boolean bkpt = basic.indexOf("ebreak") >= 0;
      StatementItem item = new StatementItem(bkpt, pc, code, basic, source);
      tlist.add(item);
      if (bkpt) {
        breakpoints.put(pc, true);
      } else {
        final int addr = pc;
        item.bkptProperty().addListener((e, o, n) -> {
          if (n) {
            breakpoints.put(addr, true);
          } else {
            breakpoints.remove(addr);
          }
        });
      }
      pc += Data.WORD_LENGTH;
    }
    Platform.runLater(() -> textTable.setRoot(new RecursiveTreeItem<>(tlist, RecursiveTreeObject::getChildren)));
  }

  /** Sets simulator text table. */
  private void setSymbolTable() {
    ArrayList<String> symFiles = new ArrayList<String>(Globals.local.keySet());
    Collections.sort(symFiles);
    ObservableList<SymbolItem> list = FXCollections.observableArrayList();
    for (String file : symFiles) {
      list.add(new SymbolItem(FS.toFile(file)));
      ArrayList<SymbolItem> other = new ArrayList<>();
      for (String name : Globals.local.get(file).labels()) {
        other.add(new SymbolItem(name, Globals.local.get(file).getSymbol(name).getAddress()));
      }
      Collections.sort(other);
      other.forEach(e -> list.add(e));
    }
    Platform.runLater(() -> symbolTable.setRoot(new RecursiveTreeItem<>(list, RecursiveTreeObject::getChildren)));
  }

  /** Sets simulator state tables. */
  public void setState() {
    xlist = FXCollections.observableArrayList();
    program.getState().xregfile().getRF().forEach(e -> {
      RegisterItem item = new RegisterItem(e.getMnemonic(), "x" + e.getNumber(), e.getValue());
      program.getState().xregfile().addObserver(item);
      xlist.add(item);
    });
    flist = FXCollections.observableArrayList();
    program.getState().fregfile().getRF().forEach(e -> {
      RegisterItem item = new RegisterItem(e.getMnemonic(), "f" + e.getNumber(), e.getValue());
      program.getState().fregfile().addObserver(item);
      flist.add(item);
    });
    mlist = FXCollections.observableArrayList();
    for (int i = START, j = 0; j < ROWS; i -= Data.WORD_LENGTH, j++) {
      int offset0 = program.getState().memory().load(i);
      int offset1 = program.getState().memory().load(i + 1);
      int offset2 = program.getState().memory().load(i + 2);
      int offset3 = program.getState().memory().load(i + 3);
      MemoryItem item = new MemoryItem(i, offset0, offset1, offset2, offset3);
      program.getState().memory().addObserver(item);
      mlist.add(item);
    }
    updateCacheOrganization();
    Platform.runLater(() -> {
      rviTable.setRoot(new RecursiveTreeItem<>(xlist, RecursiveTreeObject::getChildren));
      rvfTable.setRoot(new RecursiveTreeItem<>(flist, RecursiveTreeObject::getChildren));
      memoryTable.setRoot(new RecursiveTreeItem<>(mlist, RecursiveTreeObject::getChildren));
      segment.getSelectionModel().select(0);
      setSegment("text");
    });
  }

  /**
   * Updates an integer register.
   *
   * @param e cell edit event
   */
  private void updateXReg(CellEditEvent<RegisterItem, String> e) {
    try {
      program.getState().xregfile().setRegister("x" + e.getTreeTablePosition().getRow(), Data.atoi(e.getNewValue()));
    } catch (NumberFormatException ex) {
      rviTable.refresh();
      mainController.toast(String.format("Invalid register value: %s", e.getNewValue()), 3000);
    }
  }

  /**
   * Updates a float register.
   *
   * @param e cell edit event
   */
  private void updateFReg(CellEditEvent<RegisterItem, String> e) {
    try {
      program.getState().fregfile().setRegister("f" + e.getTreeTablePosition().getRow(), Data.atof(e.getNewValue()));
    } catch (NumberFormatException ex) {
      rvfTable.refresh();
      mainController.toast(String.format("Invalid register value: %s", e.getNewValue()), 3000);
    }
  }

  /**
   * Updates a memory byte.
   *
   * @param e cell edit event
   * @param offset byte offset
   */
  private void updateMemory(CellEditEvent<MemoryItem, String> e, int offset) {
    try {
      int addr = START - e.getTreeTablePosition().getRow() * Data.WORD_LENGTH + offset;
      program.getState().memory().store(addr, Data.atoi(e.getNewValue()));
    } catch (NumberFormatException ex) {
      memoryTable.refresh();
      mainController.toast(String.format("Invalid byte value: %s", e.getNewValue()), 3000);
    }
  }

  /** Updates memory cells. */
  private void updateMemoryCells() {
    for (int i = START, j = 0; j < ROWS; i -= Data.WORD_LENGTH, j++) {
      int offset0 = program.getState().memory().load(i);
      int offset1 = program.getState().memory().load(i + 1);
      int offset2 = program.getState().memory().load(i + 2);
      int offset3 = program.getState().memory().load(i + 3);
      mlist.get(j).update(i, offset0, offset1, offset2, offset3);
    }
  }

  /**
   * Updates RVI display mode.
   *
   * @param mode display mode
   */
  private void updateRVIDisplayMode(int mode) {
    for (RegisterItem item : xlist) {
      item.display(mode);
    }
  }

  /**
   * Updates RVF display mode.
   *
   * @param mode display mode
   */
  private void updateRVFDisplayMode(int mode) {
    for (RegisterItem item : flist) {
      item.display(mode);
    }
  }

  /**
   * Updates memory display mode.
   *
   * @param mode display mode
   */
  private void updateMemoryDisplayMode(int mode) {
    for (MemoryItem item : mlist) {
      item.display(mode);
    }
  }

  /**
   * Sets memory segment.
   *
   * @param segment memory segment
   */
  private void setSegment(String segment) {
    switch (segment) {
      case "text":
        START = Data.TEXT + (ROWS - 1) * Data.WORD_LENGTH;
        break;
      case "data":
        START = program.getState().memory().getStaticSegment() + (ROWS - 1) * Data.WORD_LENGTH;
        break;
      case "stack":
        START = Data.STACK_POINTER;
        break;
      default:
        START = program.getState().memory().getHeapSegment() + (ROWS - 1) * Data.WORD_LENGTH;
        break;
    }
    updateMemoryCells();
  }

  /**
   * Sets cache map policy.
   *
   * @param map new cache map policy
   */
  private void setCacheMap(String map) {
    switch (map) {
      case "Direct Mapped":
        Flags.CACHE_ASSOCIATIVITY = 1;
        program.getState().memory().cache().setAssociativity(1);
        ASSOC_DISABLE.set(true);
        updateCacheOrganization();
        break;
      case "N-Way Set Assoc":
        ASSOC_DISABLE.set(false);
        updateCacheOrganization();
        break;
      default:
        Flags.CACHE_ASSOCIATIVITY = Flags.CACHE_NUM_BLOCKS;
        program.getState().memory().cache().setAssociativity(Flags.CACHE_ASSOCIATIVITY);
        ASSOC_DISABLE.set(true);
        updateCacheOrganization();
        break;
    }
  }

  /**
   * Sets cache policy.
   *
   * @param policy new cache policy
   */
  private void setCachePolicy(String policy) {
    switch (policy) {
      case "LRU":
        program.getState().memory().cache().setReplacePolicy(ReplacePolicy.LRU);
        break;
      case "FIFO":
        program.getState().memory().cache().setReplacePolicy(ReplacePolicy.FIFO);
        break;
      default:
        program.getState().memory().cache().setReplacePolicy(ReplacePolicy.RAND);
        break;
    }
  }

  /** Refreshes simulator tables. */
  private void refreshSim() {
    Platform.runLater(() -> {
      rviTable.refresh();
      rvfTable.refresh();
      memoryTable.refresh();
      textTable.refresh();
      cacheTable.refresh();
      accesses.setText(String.format("%d", program.getState().memory().cache().getAccesses()));
      hits.setText(String.format("%d", program.getState().memory().cache().getHits()));
      hitRate.setText(String.format("%.2f", program.getState().memory().cache().getHitRate()));
    });
  }

  /** Updates cache organization. */
  private void updateCacheOrganization() {
    Platform.runLater(() -> {
      blockSize.setText(String.format("%d", program.getState().memory().cache().getBlockSize()));
      numBlocks.setText(String.format("%d", program.getState().memory().cache().getNumBlocks()));
      assoc.setText(String.format("%d", program.getState().memory().cache().getAssociativity()));
      cacheSize.setText(String.format("%d", program.getState().memory().cache().getCacheSize()));
      ObservableList<CacheItem> clist = FXCollections.observableArrayList();
      int size = program.getState().memory().cache().getNumBlocks();
      for (int i = 0; i < size; i++) {
        CacheItem item = new CacheItem(i);
        program.getState().memory().cache().addObserver(item);
        clist.add(item);
      }
      accesses.setText("0");
      hits.setText("0");
      hitRate.setText("0.00");
      cacheTable.setRoot(new RecursiveTreeItem<>(clist, RecursiveTreeObject::getChildren));
      cacheTable.refresh();
    });
  }

  /** Scrolls text table. */
  private void scroll() {
    Platform.runLater(() -> {
      if (textExt != null) {
        int pc = (program.getState().xregfile().getProgramCounter() - Data.TEXT) / Data.WORD_LENGTH;
        int row = Math.min(pc, tlist.size() - 1);
        if (textExt != null) {
          int first = textExt.getFirstVisibleIndex();
          int last = textExt.getLastVisibleIndex();
          if (first != -1 && last != -1 && (row >= last || row <= first)) {
            textTable.scrollTo(row);
          }
        }
      }
    });
  }

  /** Shows/hides symbol table. */
  private void symbolTable() {
    if (Settings.SHOW_ST.get()) {
      if (!hardware.getTabs().contains(symbolTableTab)) {
        hardware.getTabs().add(symbolTableTab);
      }
    } else {
      Tab selected = hardware.getSelectionModel().getSelectedItem();
      hardware.getTabs().remove(symbolTableTab);
      if (selected == symbolTableTab) {
        hardware.getSelectionModel().select(0);
      }
    }
  }

}
