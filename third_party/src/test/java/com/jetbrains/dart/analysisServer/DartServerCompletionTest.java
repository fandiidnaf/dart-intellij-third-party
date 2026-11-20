// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.dart.analysisServer;

import com.intellij.codeInsight.completion.CompletionType;
import com.intellij.codeInsight.lookup.Lookup;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.codeInsight.lookup.LookupEx;
import com.intellij.codeInsight.lookup.LookupManager;
import com.intellij.openapi.application.ReadAction;
import com.intellij.platform.backend.documentation.AsyncDocumentation;
import com.intellij.platform.backend.documentation.DocumentationData;
import com.intellij.platform.backend.documentation.DocumentationResult;
import com.intellij.platform.backend.documentation.DocumentationTarget;
import com.intellij.testFramework.PlatformTestUtil;
import com.intellij.testFramework.fixtures.CodeInsightFixtureTestCase;
import com.intellij.testFramework.fixtures.impl.CodeInsightTestFixtureImpl;
import com.intellij.util.containers.ContainerUtil;
import com.jetbrains.lang.dart.analyzer.DartAnalysisServerService;
import com.jetbrains.lang.dart.lsp.DartBridgeLspServerManager;
import com.jetbrains.lang.dart.sdk.DartConfigurable;
import com.jetbrains.lang.dart.util.DartTestUtils;
import kotlin.coroutines.EmptyCoroutineContext;
import kotlinx.coroutines.BuildersKt;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class DartServerCompletionTest extends CodeInsightFixtureTestCase {
  @Override
  public void setUp() throws Exception {
    super.setUp();
    DartTestUtils.configureDartSdk(myModule, myFixture.getTestRootDisposable(), true);
    DartAnalysisServerService.getInstance(getProject()).serverReadyForRequest();
    myFixture.setTestDataPath(DartTestUtils.BASE_TEST_DATA_PATH + getBasePath());
    ((CodeInsightTestFixtureImpl)myFixture).canChangeDocumentDuringHighlighting(true);
  }

  @Override
  protected String getBasePath() {
    return "/analysisServer/completion";
  }

  private void doTest() {
    doTest(null, Lookup.NORMAL_SELECT_CHAR);
  }

  private void doTest(@Nullable final String lookupToSelect) {
    doTest(lookupToSelect, Lookup.NORMAL_SELECT_CHAR);
  }

  private void doTest(@Nullable final String lookupToSelect, final char completionChar) {
    myFixture.configureByFile(getTestName(false) + ".dart");
    myFixture.doHighlighting(); // warm up
    myFixture.complete(CompletionType.BASIC);

    if (lookupToSelect != null) {
      selectLookup(lookupToSelect, completionChar);
    }

    myFixture.checkResultByFile(getTestName(false) + ".after.dart");
  }

  private void selectLookup(@NotNull final String lookupToSelect, final char completionChar) {
    final LookupEx activeLookup = LookupManager.getActiveLookup(getEditor());
    assertNotNull(activeLookup);

    final LookupElement lookup = ContainerUtil.find(activeLookup.getItems(), element -> lookupToSelect.equals(element.getLookupString()));

    assertNotNull(lookupToSelect + " is not in the completion list", lookup);

    activeLookup.setCurrentItem(lookup);
    myFixture.finishLookup(completionChar);
  }

  public void testFunctionWithArgsInvocation() {
    doTest("identical");
  }

  public void testKeepOldArgsOnTab() {
    doTest("identical", Lookup.REPLACE_SELECT_CHAR);
  }

  public void testArgsPlaceholderOnTab() {
    doTest("identical", Lookup.REPLACE_SELECT_CHAR);
  }

  public void testEatTailOnTab() {
    doTest("hashCode", Lookup.REPLACE_SELECT_CHAR);
  }

  public void testDoNotEatParenOnTab() {
    doTest("hashCode", Lookup.REPLACE_SELECT_CHAR);
  }

  public void testDoNotEatParenOnTab2() {
    doTest("hashCode", Lookup.REPLACE_SELECT_CHAR);
  }

  public void testDoNotEatTailOnTab() {
    doTest("parse", Lookup.REPLACE_SELECT_CHAR);
  }

  public void testDoNotEatAwaitOnTab() {
    doTest("fooBar", Lookup.REPLACE_SELECT_CHAR);
  }

  // TODO(jwren) revisit to fix or remove
//  public void testDoNotEatListOnTab() {
//    doTest("hashCode", Lookup.REPLACE_SELECT_CHAR);
//  }

  // TODO(jwren) revisit to fix or remove
//  public void testDoNotEatMapOnTab() {
//    doTest("runtimeType", Lookup.REPLACE_SELECT_CHAR);
//  }

  public void testFunctionNoArgsInvocation() {
    doTest();
  }

  // Fails due to https://github.com/dart-lang/sdk/issues/47993
  public void _testFunctionAfterShow() {
    doTest();
  }

  public void testFunctionAsArgument() {
    doTest();
  }

  public void testCaretPlacementInFor() {
    doTest("for");
  }

  public void testWithImportPrefix() {
    doTest();
  }

  public void testInsideIncompleteListLiteral() {
    myFixture.configureByFile(getTestName(false) + ".dart");
    myFixture.doHighlighting();
    myFixture.complete(CompletionType.BASIC);
    assertNotNull(myFixture.getLookup());
  }

  // TODO(jwren) revisit to fix or remove
//  public void testUriCompletionByTab() {
//    final String testName = getTestName(false);
//    myFixture.copyDirectoryToProject(testName, testName);
//
//    final VirtualFile root = ModuleRootManager.getInstance(myModule).getContentRoots()[0];
//    final VirtualFile file = VfsUtilCore.findRelativeFile(testName + "/web/foo.dart", root);
//    assertNotNull(file);
//    myFixture.openFileInEditor(file);
//
//    final EditorTestUtil.CaretAndSelectionState markers = EditorTestUtil.extractCaretAndSelectionMarkers(getEditor().getDocument());
//    getEditor().getCaretModel().moveToOffset(markers.carets().get(0).getCaretOffset(getEditor().getDocument()));
//
//    myFixture.doHighlighting();
//    myFixture.complete(CompletionType.BASIC);
//    selectLookup("package:projectName/libFile.dart", Lookup.REPLACE_SELECT_CHAR);
//    myFixture.checkResultByFile(testName + ".after.dart");
//  }

  public void testIncompleteTernary() {
    doTest("true");
  }

  // TODO(jwren) revisit to fix or remove
//  public void testSorting() {
//    myFixture.configureByText("foo.dart",
//                              """
//                                enum AXX {one, two}
//                                enum AXB {three, four}
//                                void foo({AXX x}) {}
//                                main() {
//                                  foo(x: <caret>);
//                                }""");
//    myFixture.doHighlighting();
//    myFixture.completeBasic();
//    myFixture.assertPreferredCompletionItems(0, "AXX", "AXX.one", "AXX.two", "const", "true", "false",
//                                             "AXB", "AXB.four", "AXB.three", "null", "main",
//                                             "AbstractClassInstantiationError", "ArgumentError",
//                                             "ArgumentError.notNull", "ArgumentError.value",
//                                             "AssertionError", "BidirectionalIterator");
//  }

  public void testNoCompletionAfterDigit() {
    myFixture.configureByText("foo.dart",
                              """
                                main() {
                                  return Foo(bar: 1<caret>, );
                                }
                                class Foo {
                                  final double bar, baz;
                                  const Foo({ this.bar, this.baz, });
                                }
                                """);
    myFixture.doHighlighting();
    myFixture.completeBasic();
    assertNull(myFixture.getLookup());
  }

  public void testLspNamedArgCompletionDoc() throws Exception {
    DartConfigurable.setExperimentalLspFeaturesEnabled(getProject(), true);
    DartAnalysisServerService das = DartAnalysisServerService.getInstance(getProject());
    myFixture.configureByText("main.dart",
                              """
                                /// this is A class
                                class A {
                                  /// this is a doc
                                  final String a;
                                  final String ab;

                                  new({required this.a, this.ab = ''});
                                }

                                void main(List<String> args) {
                                  A(a<caret>)
                                }
                                """);
    DartBridgeLspServerManager bridgeManager = getProject().getService(DartBridgeLspServerManager.class);
    bridgeManager.startBridgeServer();
    try {
      long deadline = System.currentTimeMillis() + 10_000;
      while (!bridgeManager.isClientConnected() && System.currentTimeMillis() < deadline) {
        PlatformTestUtil.dispatchAllEventsInIdeEventQueue();
        Thread.sleep(50);
      }
      assertTrue("Bridge LSP server should be connected", bridgeManager.isClientConnected());
      das.updateClientCapabilities(true);
      myFixture.doHighlighting();
      myFixture.complete(CompletionType.BASIC);
      LookupEx activeLookup = LookupManager.getActiveLookup(getEditor());
      assertNotNull("Expected active lookup", activeLookup);
      LookupElement aItem = ContainerUtil.find(activeLookup.getItems(), e -> e.getLookupString().startsWith("a:"));
      assertNotNull("Expected 'a:' in lookup items", aItem);
      Object obj = aItem.getObject();
      assertTrue("Expected DocumentationTarget, got " + obj.getClass(), obj instanceof DocumentationTarget);
      DocumentationTarget target = (DocumentationTarget)obj;
      DocumentationResult docResult = ReadAction.compute(() -> target.computeDocumentation());
      if (docResult instanceof AsyncDocumentation asyncDoc) {
        docResult = BuildersKt.runBlocking(EmptyCoroutineContext.INSTANCE, (scope, cont) -> asyncDoc.getSupplier().invoke(cont));
      }
      assertTrue("Expected DocumentationData, got " + docResult, docResult instanceof DocumentationData);
      String html = ((DocumentationData)docResult).getHtml();
      assertTrue("Expected field doc in HTML: " + html, html.contains("this is a doc"));
      assertTrue("Expected required String a in HTML: " + html, html.contains("required") && html.contains("String") && html.contains("a"));
    } finally {
      bridgeManager.stopBridgeServer();
      DartConfigurable.setExperimentalLspFeaturesEnabled(getProject(), false);
    }
  }

  // TODO(jwren) revisit to fix or remove
//  public void testNotYetImportedClass() {
//    doTest();
//  }

  // TODO(jwren) revisit to fix or remove
//  public void testExistingImports() {
//    myFixture.configureByFiles(getTestName(false) + ".dart", "ExistingImportLibrary.dart");
//    myFixture.doHighlighting();
//    myFixture.complete(CompletionType.BASIC);
//    final LookupEx activeLookup = LookupManager.getActiveLookup(getEditor());
//    assertNotNull(activeLookup);
//    final List<LookupElement> matches = ContainerUtil.findAll(
//      activeLookup.getItems(), element -> element.getLookupString().equals("Process"));
//    // Since we've already imported ExistingImportLibrary.dart which re-exports Process, we should only have one suggestion for it.
//    assertEquals(1, matches.size());
//    activeLookup.setCurrentItem(matches.get(0));
//    myFixture.finishLookup(Lookup.NORMAL_SELECT_CHAR);
//    myFixture.checkResultByFile(getTestName(false) + ".after.dart");
//  }

  // TODO(jwren) revisit to fix or remove
//  public void testConstructorParens() { doTest(); }
}
