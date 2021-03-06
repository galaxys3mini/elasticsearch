/*
 * Licensed to Elasticsearch under one or more contributor
 * license agreements. See the NOTICE file distributed with
 * this work for additional information regarding copyright
 * ownership. Elasticsearch licenses this file to you under
 * the Apache License, Version 2.0 (the "License"); you may
 * not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package org.elasticsearch.common.lucene.search.function;

import org.apache.lucene.index.LeafReaderContext;
import org.apache.lucene.search.Explanation;
import org.apache.lucene.search.Scorer;
import org.apache.lucene.util.BytesRef;
import org.elasticsearch.script.ScriptException;
import org.elasticsearch.script.ExplainableSearchScript;
import org.elasticsearch.script.SearchScript;

import java.io.IOException;
import java.util.Map;

public class ScriptScoreFunction extends ScoreFunction {

    static final class CannedScorer extends Scorer {
        protected int docid;
        protected float score;

        public CannedScorer() {
            super(null);
        }

        @Override
        public int docID() {
            return docid;
        }

        @Override
        public float score() throws IOException {
            return score;
        }

        @Override
        public int freq() throws IOException {
            throw new UnsupportedOperationException();
        }

        @Override
        public int nextDoc() throws IOException {
            throw new UnsupportedOperationException();
        }

        @Override
        public int advance(int target) throws IOException {
            throw new UnsupportedOperationException();
        }

        @Override
        public int nextPosition() throws IOException {
            return -1;
        }

        @Override
        public int startOffset() throws IOException {
            return -1;
        }

        @Override
        public int endOffset() throws IOException {
            return -1;
        }

        @Override
        public BytesRef getPayload() throws IOException {
            return null;
        }

        @Override
        public long cost() {
            return 1;
        }
    }

    private final String sScript;

    private final Map<String, Object> params;

    private final CannedScorer scorer;

    private final SearchScript script;
    

    public ScriptScoreFunction(String sScript, Map<String, Object> params, SearchScript script) {
        super(CombineFunction.REPLACE);
        this.sScript = sScript;
        this.params = params;
        this.script = script;
        this.scorer = new CannedScorer();
        script.setScorer(scorer);
    }

    @Override
    public void setNextReader(LeafReaderContext ctx) throws IOException {
        script.setNextReader(ctx);
    }

    @Override
    public double score(int docId, float subQueryScore) {
        script.setNextDocId(docId);
        scorer.docid = docId;
        scorer.score = subQueryScore;
        double result = script.runAsDouble();
        if (Double.isNaN(result)) {
            throw new ScriptException("script_score returned NaN");
        }
        return result;
    }

    @Override
    public Explanation explainScore(int docId, float subQueryScore) throws IOException {
        Explanation exp;
        if (script instanceof ExplainableSearchScript) {
            script.setNextDocId(docId);
            scorer.docid = docId;
            scorer.score = subQueryScore;
            exp = ((ExplainableSearchScript) script).explain(subQueryScore);
        } else {
            double score = score(docId, subQueryScore);
            String explanation = "script score function, computed with script:\"" + sScript;
            if (params != null) {
                explanation += "\" and parameters: \n" + params.toString();
            }
            exp = new Explanation(CombineFunction.toFloat(score), explanation);
        }
        return exp;
    }

    @Override
    public String toString() {
        return "script[" + sScript + "], params [" + params + "]";
    }

}