using System.Text.Json;
using Photonne.Client.Web.Models;
using Xunit;

namespace Photonne.Client.Web.Tests.Models;

/// <summary>
/// La regla viaja al servidor con los campos NO usados omitidos (el compilador
/// del servidor rechaza formas ambiguas): estos tests fijan el contrato de
/// serialización contra docs/smart-albums/rule-schema.md.
/// </summary>
public class SmartRuleNodeTests
{
    private static string Serialize(object payload) =>
        JsonSerializer.Serialize(payload, SmartRuleNode.JsonOptions);

    [Fact]
    public void A_condition_leaf_omits_every_unused_field()
    {
        var json = Serialize(new SmartRuleNode { Type = "favorite", Value = true });
        Assert.Equal("""{"type":"favorite","value":true}""", json);
    }

    [Fact]
    public void A_logical_node_serializes_op_and_children_only()
    {
        var rule = new SmartRuleNode
        {
            Op = "AND",
            Conditions = new List<SmartRuleNode>
            {
                new() { Type = "mediaType", MediaType = "Video" },
                new() { Type = "dateRange", From = new DateTime(2026, 1, 1) }
            }
        };
        var json = Serialize(rule);
        Assert.Contains("\"op\":\"AND\"", json);
        Assert.Contains("\"conditions\":[", json);
        Assert.Contains("\"mediaType\":\"Video\"", json);
        Assert.Contains("\"from\":\"2026-01-01T00:00:00\"", json);
        Assert.DoesNotContain("\"to\"", json);
        Assert.DoesNotContain("\"personIds\"", json);
        Assert.DoesNotContain("\"query\"", json);
    }

    [Fact]
    public void Person_and_label_leaves_carry_match()
    {
        var id = Guid.NewGuid();
        var person = Serialize(new SmartRuleNode { Type = "person", PersonIds = new List<Guid> { id }, Match = "all" });
        Assert.Contains($"\"personIds\":[\"{id}\"]", person);
        Assert.Contains("\"match\":\"all\"", person);

        var scene = Serialize(new SmartRuleNode { Type = "scene", Labels = new List<string> { "beach" }, Match = "any" });
        Assert.Contains("\"labels\":[\"beach\"]", scene);
    }

    [Fact]
    public void Folder_leaf_keeps_include_subfolders()
    {
        var id = Guid.NewGuid();
        var json = Serialize(new SmartRuleNode
        {
            Type = "folder",
            FolderIds = new List<Guid> { id },
            IncludeSubfolders = true
        });
        Assert.Contains("\"includeSubfolders\":true", json);
    }

    [Fact]
    public void Property_names_are_camel_case()
    {
        var json = Serialize(new SmartRuleNode { Type = "tag", TagType = "Portrait" });
        Assert.Equal("""{"type":"tag","tagType":"Portrait"}""", json);
    }
}
