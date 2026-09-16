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
