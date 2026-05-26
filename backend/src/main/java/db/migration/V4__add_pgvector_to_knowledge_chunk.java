package db.migration;

import java.sql.Statement;
import java.util.Locale;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;

public class V4__add_pgvector_to_knowledge_chunk extends BaseJavaMigration {

  @Override
  public void migrate(Context context) throws Exception {
    String databaseProductName =
        context.getConnection().getMetaData().getDatabaseProductName().toLowerCase(Locale.ROOT);
    if (!databaseProductName.contains("postgres")) {
      return;
    }

    try (Statement statement = context.getConnection().createStatement()) {
      statement.execute("create extension if not exists vector");
      statement.execute("alter table knowledge_chunk add column if not exists embedding_vector vector");
      statement.execute(
          """
          update knowledge_chunk
          set embedding_vector = cast(embedding_json as vector)
          where embedding_json is not null
            and embedding_json <> ''
            and embedding_vector is null
          """);
    }
  }
}