/*
 * Copyright 2000-2009 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.jetbrains.plugins.groovy.lang.parser.parsing.auxiliary.parameters;

import com.intellij.lang.PsiBuilder;
import com.intellij.psi.tree.IElementType;
import org.jetbrains.plugins.groovy.GroovyBundle;
import org.jetbrains.plugins.groovy.lang.parser.GroovyElementTypes;
import org.jetbrains.plugins.groovy.lang.parser.GroovyParser;
import org.jetbrains.plugins.groovy.lang.parser.parsing.auxiliary.VariableInitializer;
import org.jetbrains.plugins.groovy.lang.parser.parsing.auxiliary.annotations.Annotation;
import org.jetbrains.plugins.groovy.lang.parser.parsing.statements.typeDefinitions.ReferenceElement;
import org.jetbrains.plugins.groovy.lang.parser.parsing.types.TypeSpec;
import org.jetbrains.plugins.groovy.lang.parser.parsing.util.ParserUtils;
import org.jetbrains.plugins.groovy.lang.psi.api.auxiliary.modifiers.GrModifier;

import java.util.HashSet;
import java.util.Set;

/**
 * @author: Dmitry.Krasilschikov, ilyas
 */
public class ParameterDeclaration implements GroovyElementTypes {

  public static boolean parseForParameter(PsiBuilder builder, GroovyParser parser) {
    return parse(builder, parser, true, false);
  }

  public static boolean parseSimpleParameter(PsiBuilder builder, GroovyParser parser) {
    return parse(builder, parser, false, false);
  }

  public static boolean parseCatchParameter(PsiBuilder builder, GroovyParser parser) {
    return parse(builder, parser, false, true);
  }
  
  private static boolean parse(PsiBuilder builder, GroovyParser parser, boolean forForStatement, boolean forCatch) {
    PsiBuilder.Marker pdMarker = builder.mark();

    // Parse optional modifier(s)
    final boolean hasModifiers = parseOptionalModifier(builder, parser);

    PsiBuilder.Marker rb = builder.mark();

    int typeElementCount = 0;
    if (forForStatement) {
      final ReferenceElement.ReferenceElementResult result = TypeSpec.parseStrict(builder);

      if (true && result == ReferenceElement.ReferenceElementResult.fail && !hasModifiers) {
        rb.drop();
        pdMarker.rollbackTo();
        return false;
      }
    }
    else {
      do {
        typeElementCount++;
        final ReferenceElement.ReferenceElementResult result = TypeSpec.parseStrict(builder);
        if (result == ReferenceElement.ReferenceElementResult.fail && ParserUtils.lookAhead(builder, mBOR)) {
          builder.error(GroovyBundle.message("type.expected"));
        }
      }
      while (forCatch && ParserUtils.getToken(builder, mBOR));
    }

    if (mIDENT.equals(builder.getTokenType()) || (!forForStatement && mTRIPLE_DOT.equals(builder.getTokenType()))) {
      rb.drop();
    }
    else {
      rb.rollbackTo();
      if (forForStatement && !hasModifiers) {
        pdMarker.rollbackTo();
        return false;
      }
      else {
        typeElementCount = 0;
      }
    }

    // Possible it is a parameter, not statement
    boolean hasDots = ParserUtils.getToken(builder, mTRIPLE_DOT);

    if (ParserUtils.getToken(builder, mIDENT)) {
      if (mASSIGN.equals(builder.getTokenType())) {
        VariableInitializer.parse(builder, parser);
      }
      if (typeElementCount > 1) {
        pdMarker.done(MULTI_TYPE_PARAMETER);
      }
      else {
        pdMarker.done(PARAMETER);
      }
      return true;
    }
    else {
      // If has triple dots
      if (hasDots) {
        pdMarker.error(GroovyBundle.message("identifier.expected"));
        return true;
      }
      else {
        pdMarker.rollbackTo();
        return false;
      }
    }
  }

  /**
   * Parses optional modifiers
   *
   * @param builder Given builder
   */
  private static boolean parseOptionalModifier(PsiBuilder builder, GroovyParser parser) {

    Set<IElementType> modSet = new HashSet<IElementType>();

    PsiBuilder.Marker marker = builder.mark();

    boolean hasModifiers = false;
    while (builder.getTokenType() == kFINAL ||
            builder.getTokenType() == kDEF ||
            builder.getTokenType() == mAT) {
      hasModifiers = true;
      if (kFINAL.equals(builder.getTokenType())) {
        if (modSet.contains(kFINAL)) {
          ParserUtils.wrapError(builder, GroovyBundle.message("duplicate.modifier", GrModifier.FINAL));
        } else {
          builder.advanceLexer();
          modSet.add(kFINAL);
        }
        ParserUtils.getToken(builder, mNLS);
      } else if (kDEF.equals(builder.getTokenType())) {
        if (modSet.contains(kDEF)) {
          ParserUtils.wrapError(builder, GroovyBundle.message("duplicate.modifier", GrModifier.DEF));
        } else {
          builder.advanceLexer();
          modSet.add(kDEF);
        }
        ParserUtils.getToken(builder, mNLS);
      } else if (Annotation.parse(builder, parser)) {
        ParserUtils.getToken(builder, mNLS);
      }
    }
    marker.done(MODIFIERS);
    return hasModifiers;
  }

}
