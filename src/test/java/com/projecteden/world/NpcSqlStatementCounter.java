package com.projecteden.world;

import java.util.Locale;
import java.util.concurrent.atomic.LongAdder;
import org.hibernate.resource.jdbc.spi.StatementInspector;

public final class NpcSqlStatementCounter implements StatementInspector {
    private static final LongAdder SELECTS = new LongAdder();
    private static final LongAdder UPDATES = new LongAdder();
    private static final LongAdder INSERTS = new LongAdder();
    private static final LongAdder DELETES = new LongAdder();

    @Override
    public String inspect(String sql) {
        String normalized = sql.stripLeading().toLowerCase(Locale.ROOT);
        if (normalized.startsWith("select")) SELECTS.increment();
        else if (normalized.startsWith("update")) UPDATES.increment();
        else if (normalized.startsWith("insert")) INSERTS.increment();
        else if (normalized.startsWith("delete")) DELETES.increment();
        return sql;
    }

    public static void reset() {
        SELECTS.reset();
        UPDATES.reset();
        INSERTS.reset();
        DELETES.reset();
    }

    public static Counts snapshot() {
        return new Counts(SELECTS.sum(), UPDATES.sum(), INSERTS.sum(), DELETES.sum());
    }

    public record Counts(long selects, long updates, long inserts, long deletes) {
        public long total() { return selects + updates + inserts + deletes; }
    }
}
