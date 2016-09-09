/*
 * DBeaver - Universal Database Manager
 * Copyright (C) 2010-2016 Serge Rieder (serge@jkiss.org)
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License (version 2)
 * as published by the Free Software Foundation.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along
 * with this program; if not, write to the Free Software Foundation, Inc.,
 * 51 Franklin Street, Fifth Floor, Boston, MA 02110-1301 USA.
 */
package org.jkiss.dbeaver.ui.editors.sql.syntax;

import org.eclipse.jface.text.BadLocationException;
import org.eclipse.jface.text.IDocument;
import org.eclipse.jface.text.ITypedRegion;
import org.eclipse.jface.text.TextUtilities;
import org.eclipse.jface.text.rules.*;
import org.jkiss.dbeaver.model.sql.SQLConstants;
import org.jkiss.dbeaver.model.sql.SQLDialect;
import org.jkiss.utils.Pair;

import java.util.ArrayList;
import java.util.List;

/**
 * The <code>SQLPartitionScanner</code>, a subclass of a <code>RulesBasedPartitionScanner</code>,
 * is responsible for dynamically computing the partitions of its
 * SQL document as events signal that the document has
 * changed. The document partitions are based on tokens that represent comments
 * and SQL code sections.
 */
public class SQLPartitionScanner extends RuleBasedPartitionScanner {
    public final static String SQL_PARTITIONING = "___sql_partitioning";

    public final static String CONTENT_TYPE_SQL_COMMENT = "sql_comment";
    public final static String CONTENT_TYPE_SQL_MULTILINE_COMMENT = "sql_multiline_comment";
    public final static String CONTENT_TYPE_SQL_STRING = "sql_character";
    public final static String CONTENT_TYPE_SQL_QUOTED = "sql_quoted";

    public final static String[] SQL_CONTENT_TYPES = new String[]{
        IDocument.DEFAULT_CONTENT_TYPE,
        CONTENT_TYPE_SQL_COMMENT,
        CONTENT_TYPE_SQL_MULTILINE_COMMENT,
        CONTENT_TYPE_SQL_STRING,
        CONTENT_TYPE_SQL_QUOTED,
    };

    // Syntax higlight
    private final List<IPredicateRule> rules = new ArrayList<>();
    private final IToken commentToken = new Token(CONTENT_TYPE_SQL_COMMENT);
    private final IToken multilineCommentToken = new Token(CONTENT_TYPE_SQL_MULTILINE_COMMENT);
    private final IToken sqlStringToken = new Token(CONTENT_TYPE_SQL_STRING);
    private final IToken sqlQuotedToken = new Token(CONTENT_TYPE_SQL_QUOTED);

    /**
     * Detector for empty comments.
     */
    static class EmptyCommentDetector implements IWordDetector {

        /*
         * @see IWordDetector#isWordStart
         */
        @Override
        public boolean isWordStart(char c)
        {
            return (c == '/');
        }

        /*
         * @see IWordDetector#isWordPart
         */
        @Override
        public boolean isWordPart(char c)
        {
            return (c == '*' || c == '/');
        }
    }

    /**
     * Word rule for empty comments.
     */
    static class EmptyCommentRule extends WordRule implements IPredicateRule {

        private IToken successToken;

        public EmptyCommentRule(IToken successToken)
        {
            super(new EmptyCommentDetector());
            this.successToken = successToken;
            addWord("/**/", this.successToken); //$NON-NLS-1$
        }

        @Override
        public IToken evaluate(ICharacterScanner scanner, boolean resume)
        {
            return evaluate(scanner);
        }

        @Override
        public IToken getSuccessToken()
        {
            return successToken;
        }
    }

    private void setupRules()
    {
        IPredicateRule[] result = new IPredicateRule[rules.size()];
        rules.toArray(result);
        setPredicateRules(result);
    }

    private void initRules(SQLDialect dialect)
    {
        rules.add(new MultiLineRule(SQLConstants.STR_QUOTE_SINGLE, SQLConstants.STR_QUOTE_SINGLE, sqlStringToken, '\\'));
        rules.add(new MultiLineRule(SQLConstants.STR_QUOTE_DOUBLE, SQLConstants.STR_QUOTE_DOUBLE, sqlQuotedToken, '\\'));

        for (String lineComment : dialect.getSingleLineComments()) {
            rules.add(new EndOfLineRule(lineComment, commentToken));
        }

        // Add special case word rule.
        EmptyCommentRule wordRule = new EmptyCommentRule(multilineCommentToken);
        rules.add(wordRule);

        // Add rules for multi-line comments
        Pair<String, String> multiLineComments = dialect.getMultiLineComments();
        if (multiLineComments != null) {
            rules.add(new MultiLineRule(multiLineComments.getFirst(), multiLineComments.getSecond(), multilineCommentToken, (char) 0, true));
        }

        String[] singleLineComments = dialect.getSingleLineComments();

        for (String singleLineComment : singleLineComments) {
            // Add rule for single line comments.
            rules.add(new EndOfLineRule(singleLineComment, commentToken));
        }
    }

    public SQLPartitionScanner(SQLDialect dialect)
    {
        initRules(dialect);
        setupRules();
    }

    /**
     * Return the String ranging from the start of the current partition to the current scanning position. Some rules
     * (@see NestedMultiLineRule) need this information to calculate the comment nesting depth.
     * @return value
     */
    public String getScannedPartitionString()
    {
        try {
            return fDocument.get(fPartitionOffset, fOffset - fPartitionOffset);
        }
        catch (Exception e) {
            // Do nothing
        }
        return "";
    }

    /**
     * Gets the partitions of the given document as an array of
     * <code>ITypedRegion</code> objects.  There is a distinct non-overlapping partition
     * for each comment line, string literal, delimited identifier, and "everything else"
     * (that is, SQL code other than a string literal or delimited identifier).
     *
     * @param doc the document to parse into partitions
     * @return an array containing the document partion regions
     */
    public static ITypedRegion[] getDocumentRegions(IDocument doc)
    {
        ITypedRegion[] regions = null;
        try {
            regions = TextUtilities.computePartitioning(doc, SQLPartitionScanner.SQL_PARTITIONING, 0, doc.getLength(), false);
        }
        catch (BadLocationException e) {
            // ignore
        }

        return regions;
    }

}