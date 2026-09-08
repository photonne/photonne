using Photonne.Client.Web.Services;
using Xunit;

namespace Photonne.Client.Web.Tests.Services;

public class GridKeyboardNavTests
{
    private static readonly Guid[][] Grid =
    {
        new[] { Guid.NewGuid(), Guid.NewGuid(), Guid.NewGuid() }, // fila 0: a b c
        new[] { Guid.NewGuid(), Guid.NewGuid() },                 // fila 1: d e
        new[] { Guid.NewGuid(), Guid.NewGuid(), Guid.NewGuid() }  // fila 2: f g h
    };

    [Fact]
    public void First_arrow_press_focuses_the_first_cell()
    {
        Assert.Equal(Grid[0][0], GridKeyboardNav.Move(Grid, null, "down"));
        Assert.Equal(Grid[0][0], GridKeyboardNav.Move(Grid, Guid.NewGuid(), "right"));
    }

    [Fact]
    public void Left_and_right_wrap_across_rows_but_stop_at_the_ends()
    {
        Assert.Equal(Grid[1][0], GridKeyboardNav.Move(Grid, Grid[0][2], "right"));
        Assert.Equal(Grid[0][2], GridKeyboardNav.Move(Grid, Grid[1][0], "left"));
        Assert.Equal(Grid[0][0], GridKeyboardNav.Move(Grid, Grid[0][0], "left"));
        Assert.Equal(Grid[2][2], GridKeyboardNav.Move(Grid, Grid[2][2], "right"));
    }

    [Fact]
    public void Up_and_down_keep_the_column_clamped_to_the_shorter_row()
    {
        // c (fila 0, col 2) ↓ → fila 1 solo tiene 2: clampa a e (col 1).
        Assert.Equal(Grid[1][1], GridKeyboardNav.Move(Grid, Grid[0][2], "down"));
        Assert.Equal(Grid[2][1], GridKeyboardNav.Move(Grid, Grid[1][1], "down"));
        Assert.Equal(Grid[0][1], GridKeyboardNav.Move(Grid, Grid[1][1], "up"));
        // Bordes: quieto.
        Assert.Equal(Grid[0][1], GridKeyboardNav.Move(Grid, Grid[0][1], "up"));
        Assert.Equal(Grid[2][0], GridKeyboardNav.Move(Grid, Grid[2][0], "down"));
    }
}
