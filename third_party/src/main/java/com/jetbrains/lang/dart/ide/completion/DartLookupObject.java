// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.lang.dart.ide.completion;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.psi.util.PsiTreeUtil;
import com.jetbrains.lang.dart.analyzer.DartAnalysisServerService;
import com.jetbrains.lang.dart.analyzer.DartFileInfo;
import com.jetbrains.lang.dart.analyzer.DartFileInfoKt;
import com.jetbrains.lang.dart.psi.DartComponent;
import com.jetbrains.lang.dart.psi.DartComponentName;
import com.jetbrains.lang.dart.psi.DartFieldFormalParameter;
import com.jetbrains.lang.dart.psi.DartId;
import com.jetbrains.lang.dart.psi.DartReferenceExpression;
import org.dartlang.analysis.server.protocol.Location;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class DartLookupObject {
  private final @NotNull Project myProject;
  private final @Nullable Location myLocation;
  private final int myRelevance;

  public DartLookupObject(final @NotNull Project project, final @Nullable Location location, final int relevance) {
    myProject = project;
    myLocation = location;
    myRelevance = relevance;
  }

  public int getRelevance() {
    return myRelevance;
  }

  /**
   * This method may parse source code, so use it responsibly: do not call it for all DartLookupObjects from the completion list.
   */
  public @Nullable PsiElement findPsiElement() {
    if (myLocation == null) return null;

    String filePathOrUri = myLocation.getFile();
    DartFileInfo fileInfo = DartFileInfoKt.getDartFileInfo(myProject, filePathOrUri);
    VirtualFile vFile = fileInfo.findFile();
    if (vFile == null) {
      vFile = com.intellij.openapi.vfs.VirtualFileManager.getInstance().findFileByUrl(filePathOrUri);
    }
    if (vFile == null && !filePathOrUri.contains("://")) {
      vFile = com.intellij.openapi.vfs.VirtualFileManager.getInstance().findFileByUrl("file://" + filePathOrUri);
    }
    if (vFile == null && !filePathOrUri.contains("://")) {
      vFile = com.intellij.openapi.vfs.VirtualFileManager.getInstance().findFileByUrl("temp://" + filePathOrUri);
    }
    final PsiFile psiFile = vFile == null ? null : PsiManager.getInstance(myProject).findFile(vFile);
    if (psiFile != null) {
      final int offset = DartAnalysisServerService.getInstance(myProject).getConvertedOffset(vFile, myLocation.getOffset());
      final PsiElement elementAtOffset = psiFile.findElementAt(offset);
      if (elementAtOffset != null) {
        final DartComponentName componentName = PsiTreeUtil.getParentOfType(elementAtOffset, DartComponentName.class);
        if (componentName != null) {
          return componentName;
        }
        final DartFieldFormalParameter fieldFormalParameter = PsiTreeUtil.getParentOfType(elementAtOffset, DartFieldFormalParameter.class);
        if (fieldFormalParameter != null) {
          PsiElement target = fieldFormalParameter.getReferenceExpression().resolve();
          if (target == null) {
            final com.jetbrains.lang.dart.psi.DartClass dartClass = PsiTreeUtil.getParentOfType(fieldFormalParameter, com.jetbrains.lang.dart.psi.DartClass.class);
            if (dartClass != null) {
              final java.util.List<DartComponent> members = dartClass.findMembersByName(fieldFormalParameter.getReferenceExpression().getText());
              if (!members.isEmpty()) {
                target = members.get(0);
              }
            }
          }
          if (target != null) {
            return target;
          }
        }
        final DartReferenceExpression referenceExpression = PsiTreeUtil.getParentOfType(elementAtOffset, DartReferenceExpression.class);
        if (referenceExpression != null) {
          final PsiElement target = referenceExpression.resolve();
          if (target != null) {
            return target;
          }
        }
        final DartComponent component = PsiTreeUtil.getParentOfType(elementAtOffset, DartComponent.class);
        if (component != null) {
          return component;
        }
        if (elementAtOffset.getParent() instanceof DartId && elementAtOffset.getTextRange().getStartOffset() == offset) {
          return elementAtOffset; // example in WEB-25478 (https://github.com/flutter/flutter-intellij/issues/385#issuecomment-278826063)
        }
      }
    }
    return null;
  }
}
