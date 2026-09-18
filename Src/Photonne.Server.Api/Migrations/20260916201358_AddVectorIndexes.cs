using Microsoft.EntityFrameworkCore.Migrations;

#nullable disable

namespace Photonne.Server.Api.Migrations
{
    /// <inheritdoc />
    public partial class AddVectorIndexes : Migration
    {
        /// <inheritdoc />
        protected override void Up(MigrationBuilder migrationBuilder)
        {
            // pgvector builds an HNSW graph in memory when it fits in
            // maintenance_work_mem and falls back to a far slower on-disk build
            // when it doesn't. 160k vectors of 512 floats plus the graph is a
            // few hundred MB; the default 64 MB would not hold it. LOCAL: only
            // this migration's transaction, nothing persists.
            migrationBuilder.Sql("SET LOCAL maintenance_work_mem = '1GB';");

            // A parallel build puts that whole budget in a dynamic shared
            // memory segment, which lives in /dev/shm — 64 MB in a Docker
            // container unless the compose sets shm_size — so the CREATE INDEX
            // died with "could not resize shared memory segment: No space left
            // on device" on every startup. A serial build keeps the graph in
            // the backend's own memory; it costs the parallelism, which a
            // one-core Postgres never had anyway.
            migrationBuilder.Sql("SET LOCAL max_parallel_maintenance_workers = 0;");

            migrationBuilder.CreateIndex(
                name: "IX_Faces_Embedding",
                table: "Faces",
                column: "Embedding")
                .Annotation("Npgsql:IndexMethod", "hnsw")
                .Annotation("Npgsql:IndexOperators", new[] { "vector_cosine_ops" });

            migrationBuilder.CreateIndex(
                name: "IX_AssetEmbeddings_Embedding",
                table: "AssetEmbeddings",
                column: "Embedding")
                .Annotation("Npgsql:IndexMethod", "hnsw")
                .Annotation("Npgsql:IndexOperators", new[] { "vector_cosine_ops" });
        }

        /// <inheritdoc />
        protected override void Down(MigrationBuilder migrationBuilder)
        {
            migrationBuilder.DropIndex(
                name: "IX_Faces_Embedding",
                table: "Faces");

            migrationBuilder.DropIndex(
                name: "IX_AssetEmbeddings_Embedding",
                table: "AssetEmbeddings");
        }
    }
}
