using System.Text.Json;
using System.Text.Json.Serialization;
using Pgvector;

namespace Photonne.Server.Api.Features.DatabaseBackup;

// Default JSON serialization can't round-trip Pgvector.Vector (no parameterless ctor,
// internal storage is ReadOnlyMemory<float>). We persist it as a plain float array,
// which is what every embedding model already produces.
public class VectorJsonConverter : JsonConverter<Vector>
{
    public override Vector? Read(ref Utf8JsonReader reader, Type typeToConvert, JsonSerializerOptions options)
    {
        if (reader.TokenType == JsonTokenType.Null) return null;

        var floats = JsonSerializer.Deserialize<float[]>(ref reader, options) ?? [];
        return new Vector(floats);
    }

    public override void Write(Utf8JsonWriter writer, Vector value, JsonSerializerOptions options)
    {
        JsonSerializer.Serialize(writer, value.ToArray(), options);
    }
}
