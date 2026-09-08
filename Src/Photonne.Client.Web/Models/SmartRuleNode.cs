using System.Text.Json;
using System.Text.Json.Serialization;

namespace Photonne.Client.Web.Models;

/// <summary>
/// Espejo del SmartRuleNode del servidor (esquema en docs/smart-albums/rule-schema.md):
/// o nodo LÓGICO (Op "AND"/"OR" + Conditions) o hoja de CONDICIÓN (Type +
/// su payload), o "not" (Type="not" + Condition). Los campos no usados deben
/// OMITIRSE del JSON — serializar siempre con <see cref="JsonOptions"/>.
/// </summary>
public class SmartRuleNode
{
    /// <summary>Opciones con nulls omitidos, obligatorias al enviar reglas.</summary>
    public static readonly JsonSerializerOptions JsonOptions = new(JsonSerializerDefaults.Web)
    {
        DefaultIgnoreCondition = JsonIgnoreCondition.WhenWritingNull
    };

    // ── Nodo lógico ───────────────────────────────────────────────────────
    public string? Op { get; set; }
    public List<SmartRuleNode>? Conditions { get; set; }

    // ── Hoja de condición ─────────────────────────────────────────────────
    public string? Type { get; set; }
    /// <summary>Hijo de un nodo "not".</summary>
    public SmartRuleNode? Condition { get; set; }

    /// <summary>"any" (OR entre valores) o "all" (AND / intersección).</summary>
    public string? Match { get; set; }

    public List<Guid>? PersonIds { get; set; }
    public List<Guid>? FolderIds { get; set; }
    public bool? IncludeSubfolders { get; set; }
    public DateTime? From { get; set; }
    public DateTime? To { get; set; }
    public List<string>? Labels { get; set; }
    public List<Guid>? UserTagIds { get; set; }
    public string? TagType { get; set; }
    public bool? Value { get; set; }
    public string? MediaType { get; set; }
    public string? Query { get; set; }

    public bool IsLogical => Op != null;
}
