using System.Text.Json;
using System.Text.Json.Nodes;
using Photonne.Server.Api.Tests.Infrastructure;

namespace Photonne.Server.Api.Tests.Contract;

/// <summary>
/// The OpenAPI document is the API's contract: the web client is generated
/// from it and the published apps depend on it. It is committed at
/// <c>Src/Photonne.Server.Api/openapi/v1.json</c>, so every change to the
/// contract shows up in review as a diff of that file.
///
/// After changing an endpoint, regenerate it with
/// <c>UPDATE_OPENAPI_SNAPSHOT=1 dotnet test --filter OpenApiContractTests</c>
/// and commit the result.
/// </summary>
[Collection(IntegrationCollection.Name)]
public class OpenApiContractTests
{
    /// <summary>
    /// Operations whose success response the document doesn't describe (a bare
    /// "200 OK" with no schema), so a generated client can't type them. Only
    /// allowed to go down: convert an endpoint to TypedResults (or declare
    /// <c>.Produces&lt;T&gt;()</c>) and lower this number.
    /// </summary>
    private const int UntypedOperationsBaseline = 0;

    private static readonly JsonSerializerOptions Indented = new() { WriteIndented = true };

    private readonly PhotonneApiFactory _factory;

    public OpenApiContractTests(PhotonneApiFactory factory) => _factory = factory;

    [Fact]
    public async Task Document_MatchesTheCommittedSnapshot()
    {
        var current = await FetchDocumentAsync();
        var snapshotPath = SnapshotPath();

        if (Environment.GetEnvironmentVariable("UPDATE_OPENAPI_SNAPSHOT") == "1")
        {
            Directory.CreateDirectory(Path.GetDirectoryName(snapshotPath)!);
            await File.WriteAllTextAsync(snapshotPath, current);
            return;
        }

        Assert.True(File.Exists(snapshotPath), $"Missing {snapshotPath}. Run with UPDATE_OPENAPI_SNAPSHOT=1.");
        var committed = Normalize(await File.ReadAllTextAsync(snapshotPath));
        Assert.True(committed == current,
            "The API contract changed. If intended, run the contract tests with UPDATE_OPENAPI_SNAPSHOT=1 " +
            "and commit Src/Photonne.Server.Api/openapi/v1.json.");
    }

    [Fact]
    public async Task UntypedOperations_DoNotGrow()
    {
        var document = JsonNode.Parse(await FetchDocumentAsync())!;
        var untyped = new List<string>();
        foreach (var (path, item) in document["paths"]!.AsObject())
        {
            foreach (var (method, operation) in item!.AsObject())
            {
                var responses = operation!["responses"]?.AsObject();
                if (responses is null) continue;
                var described = responses.Any(r =>
                    r.Key.StartsWith('2') && (r.Key != "200" || r.Value!["content"] is not null));
                if (!described) untyped.Add($"{method.ToUpperInvariant()} {path}");
            }
        }

        Assert.True(untyped.Count <= UntypedOperationsBaseline,
            $"{untyped.Count} untyped operations, baseline {UntypedOperationsBaseline}. New endpoints must " +
            "declare their response type (TypedResults or .Produces<T>()).");
        Assert.True(untyped.Count >= UntypedOperationsBaseline,
            $"Down to {untyped.Count} untyped operations: lower UntypedOperationsBaseline to lock it in.");
    }

    private async Task<string> FetchDocumentAsync()
        => Normalize(await _factory.CreateClient().GetStringAsync("/openapi/v1.json"));

    private static string Normalize(string json)
        => JsonNode.Parse(json)!.ToJsonString(Indented).ReplaceLineEndings("\n") + "\n";

    private static string SnapshotPath()
    {
        var dir = new DirectoryInfo(AppContext.BaseDirectory);
        while (dir != null && !File.Exists(Path.Combine(dir.FullName, "Photonne.sln")))
            dir = dir.Parent;
        if (dir == null) throw new InvalidOperationException("Photonne.sln not found above the test output.");
        return Path.Combine(dir.FullName, "Src", "Photonne.Server.Api", "openapi", "v1.json");
    }
}
