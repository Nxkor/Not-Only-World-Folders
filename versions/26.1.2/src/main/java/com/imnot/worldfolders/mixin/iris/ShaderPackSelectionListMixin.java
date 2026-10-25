package com.imnot.worldfolders.mixin.iris;

import com.imnot.worldfolders.Hooks;
import com.imnot.worldfolders.Shaders;
import net.irisshaders.iris.Iris;
import net.irisshaders.iris.gui.element.ShaderPackSelectionList;
import net.minecraft.client.gui.components.AbstractSelectionList;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

/** Right-click on a pack: its menu (trash...). The click comes through AbstractContainerWidgetMixin. */
@Mixin(ShaderPackSelectionList.class)
public abstract class ShaderPackSelectionListMixin extends AbstractSelectionList<ShaderPackSelectionList.BaseEntry> implements Hooks.RowMenu {
	private ShaderPackSelectionListMixin() {
		super(null, 0, 0, 0, 0);
	}

	@Override
	public boolean worldfolders$rowMenu(double x, double y) {
		if (!(worldfolders$rowAt(y) instanceof ShaderPackSelectionList.ShaderPackEntry pack) || !isMouseOver(x, y)) return false;
		ShaderPackSelectionList self = (ShaderPackSelectionList) (Object) this;
		Shaders.menu(Iris.getShaderpacksDirectory(), pack.getPackName(), () -> {
			self.refresh();
			setScrollAmount(scrollAmount()); // fewer rows: back inside the list
		});
		return true;
	}

	/** From where the rows are drawn: the rows of Iris are not all one height apart (26.x), getEntryAtPosition can miss. */
	@Unique
	private ShaderPackSelectionList.BaseEntry worldfolders$rowAt(double y) {
		for (int i = 0; i < children().size(); i++) {
			int top = getRowTop(i), bottom = i + 1 < children().size() ? getRowTop(i + 1) : top + 20;
			if (y >= top && y < bottom) return children().get(i);
		}
		return null;
	}
}
