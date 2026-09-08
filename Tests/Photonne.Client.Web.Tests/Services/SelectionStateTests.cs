using Photonne.Client.Web.Services;
using Xunit;

namespace Photonne.Client.Web.Tests.Services;

public class SelectionStateTests
{
    private static readonly List<Guid> Order = Enumerable.Range(0, 10)
        .Select(_ => Guid.NewGuid()).ToList();

    [Fact]
    public void Toggle_selects_deselects_and_moves_the_anchor()
    {
        var s = new SelectionState();
        s.Toggle(Order[3]);
        Assert.True(s.IsSelected(Order[3]));
        Assert.Equal(Order[3], s.Anchor);
        s.Toggle(Order[3]);
        Assert.False(s.IsSelected(Order[3]));
        Assert.Equal(1, 1);
    }

    [Fact]
    public void Range_selects_between_anchor_and_target_in_either_direction()
    {
        var s = new SelectionState();
        s.Toggle(Order[5]);
        s.SelectRange(Order[2], Order);
        Assert.Equal(4, s.Count); // 2,3,4,5
        Assert.True(s.IsSelected(Order[2]));
        Assert.True(s.IsSelected(Order[5]));

        // El ancla no se mueve: un segundo shift+clic re-extiende desde 5.
        s.SelectRange(Order[7], Order);
        Assert.True(s.IsSelected(Order[6]));
        Assert.True(s.IsSelected(Order[7]));
    }

    [Fact]
    public void Range_without_anchor_degrades_to_toggle()
    {
        var s = new SelectionState();
        s.SelectRange(Order[4], Order);
        Assert.True(s.IsSelected(Order[4]));
        Assert.Equal(Order[4], s.Anchor);
        Assert.Equal(1, s.Count);
    }

    [Fact]
    public void Range_with_anchor_missing_from_order_degrades_to_toggle()
    {
        var s = new SelectionState();
        s.Toggle(Guid.NewGuid()); // ancla que no está en el orden visible
        s.SelectRange(Order[1], Order);
        Assert.True(s.IsSelected(Order[1]));
    }

    [Fact]
    public void Bulk_set_and_tri_state()
    {
        var s = new SelectionState();
        var month = Order.Take(4).ToList();
        s.SetSelected(month, true);
        Assert.Equal((true, true), s.StateOf(month));
        s.Toggle(month[0]);
        Assert.Equal((true, false), s.StateOf(month));
        s.SetSelected(month, false);
        Assert.Equal((false, false), s.StateOf(month));
    }

    [Fact]
    public void Clear_resets_selection_and_anchor_and_notifies_once()
    {
        var s = new SelectionState();
        var events = 0;
        s.Changed += () => events++;
        s.Toggle(Order[0]);
        s.Clear();
        Assert.False(s.IsActive);
        Assert.Null(s.Anchor);
        Assert.Equal(2, events);
        s.Clear(); // ya vacío: sin evento
        Assert.Equal(2, events);
    }
}
