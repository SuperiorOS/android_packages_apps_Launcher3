/*
 * Copyright (C) 2008 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.launcher3;

import static com.android.app.animation.Interpolators.LINEAR;
import static com.android.launcher3.InvariantDeviceProfile.INDEX_DEFAULT;
import static com.android.launcher3.InvariantDeviceProfile.INDEX_LANDSCAPE;
import static com.android.launcher3.InvariantDeviceProfile.INDEX_TWO_PANEL_LANDSCAPE;
import static com.android.launcher3.InvariantDeviceProfile.INDEX_TWO_PANEL_PORTRAIT;
import static com.android.launcher3.Utilities.dpiFromPx;
import static com.android.launcher3.Utilities.pxFromSp;
import static com.android.launcher3.config.FeatureFlags.ENABLE_MULTI_DISPLAY_PARTIAL_DEPTH;
import static com.android.launcher3.folder.ClippedFolderIconLayoutRule.ICON_OVERLAP_FACTOR;
import static com.android.launcher3.icons.GraphicsUtils.getShapePath;
import static com.android.launcher3.icons.IconNormalizer.ICON_VISIBLE_AREA_FACTOR;
import static com.android.launcher3.testing.shared.ResourceUtils.INVALID_RESOURCE_HANDLE;
import static com.android.launcher3.testing.shared.ResourceUtils.pxFromDp;
import static com.android.launcher3.testing.shared.ResourceUtils.roundPxValueFromFloat;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.content.res.TypedArray;
import android.graphics.Point;
import android.graphics.PointF;
import android.graphics.Rect;
import android.util.DisplayMetrics;
import android.util.SparseArray;
import android.view.Surface;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.res.ResourcesCompat;

import com.android.launcher3.CellLayout.ContainerType;
import com.android.launcher3.DevicePaddings.DevicePadding;
import com.android.launcher3.config.FeatureFlags;
import com.android.launcher3.icons.DotRenderer;
import com.android.launcher3.icons.IconNormalizer;
import com.android.launcher3.model.data.ItemInfo;
import com.android.launcher3.responsive.AllAppsSpecs;
import com.android.launcher3.responsive.CalculatedAllAppsSpec;
import com.android.launcher3.responsive.CalculatedFolderSpec;
import com.android.launcher3.responsive.CalculatedHotseatSpec;
import com.android.launcher3.responsive.CalculatedWorkspaceSpec;
import com.android.launcher3.responsive.FolderSpecs;
import com.android.launcher3.responsive.HotseatSpecs;
import com.android.launcher3.responsive.WorkspaceSpecs;
import com.android.launcher3.uioverrides.ApiWrapper;
import com.android.launcher3.util.DisplayController;
import com.android.launcher3.util.DisplayController.Info;
import com.android.launcher3.util.IconSizeSteps;
import com.android.launcher3.util.ResourceHelper;
import com.android.launcher3.util.WindowBounds;

import java.io.PrintWriter;
import java.util.Locale;
import java.util.function.Consumer;

@SuppressLint("NewApi")
public class DeviceProfile {

    public static final String KEY_ROW_HEIGHT = "pref_row_height";

    private static final int DEFAULT_DOT_SIZE = 100;
    private static final float ALL_APPS_TABLET_MAX_ROWS = 5.5f;
    private static final float MIN_FOLDER_TEXT_SIZE_SP = 16f;
    private static final float MIN_WIDGET_PADDING_DP = 6f;

    public static final PointF DEFAULT_SCALE = new PointF(1.0f, 1.0f);
    public static final ViewScaleProvider DEFAULT_PROVIDER = itemInfo -> DEFAULT_SCALE;
    public static final Consumer<DeviceProfile> DEFAULT_DIMENSION_PROVIDER = dp -> {};

    public final InvariantDeviceProfile inv;
    private final Info mInfo;
    private final DisplayMetrics mMetrics;
    private final IconSizeSteps mIconSizeSteps;

    // Device properties
    public final boolean isTablet;
    public final boolean isPhone;
    public final boolean transposeLayoutWithOrientation;
    public final boolean isMultiDisplay;
    public final boolean isTwoPanels;
    public final boolean isQsbInline;

    // Device properties in current orientation
    public final boolean isLandscape;
    public final boolean isMultiWindowMode;
    public final boolean isGestureMode;

    public final int windowX;
    public final int windowY;
    public final int widthPx;
    public final int heightPx;
    public final int availableWidthPx;
    public final int availableHeightPx;
    public final int rotationHint;

    public final float aspectRatio;

    private final boolean mIsScalableGrid;
    private final int mTypeIndex;

    // Responsive grid
    private final boolean mIsResponsiveGrid;
    private CalculatedWorkspaceSpec mResponsiveWidthSpec;
    private CalculatedWorkspaceSpec mResponsiveHeightSpec;
    private CalculatedAllAppsSpec mAllAppsResponsiveWidthSpec;
    private CalculatedAllAppsSpec mAllAppsResponsiveHeightSpec;
    private CalculatedFolderSpec mResponsiveFolderWidthSpec;
    private CalculatedFolderSpec mResponsiveFolderHeightSpec;
    private CalculatedHotseatSpec mResponsiveHotseatSpec;

    /**
     * The maximum amount of left/right workspace padding as a percentage of the screen width.
     * To be clear, this means that up to 7% of the screen width can be used as left padding, and
     * 7% of the screen width can be used as right padding.
     */
    private static final float MAX_HORIZONTAL_PADDING_PERCENT = 0.14f;

    private static final float TALL_DEVICE_ASPECT_RATIO_THRESHOLD = 2.0f;
    private static final float TALLER_DEVICE_ASPECT_RATIO_THRESHOLD = 2.15f;
    private static final float TALL_DEVICE_EXTRA_SPACE_THRESHOLD_DP = 252;
    private static final float TALL_DEVICE_MORE_EXTRA_SPACE_THRESHOLD_DP = 268;

    // Workspace
    public final int desiredWorkspaceHorizontalMarginOriginalPx;
    public int desiredWorkspaceHorizontalMarginPx;
    public int gridVisualizationPaddingX;
    public int gridVisualizationPaddingY;
    public Point cellLayoutBorderSpaceOriginalPx;
    public Point cellLayoutBorderSpacePx;
    public Rect cellLayoutPaddingPx = new Rect();

    public final int edgeMarginPx;
    public final float workspaceContentScale;
    public final int workspaceSpringLoadedMinNextPageVisiblePx;

    private final int extraSpace;
    private int maxEmptySpace;
    public int workspaceTopPadding;
    public int workspaceBottomPadding;

    // Workspace page indicator
    public final int workspacePageIndicatorHeight;
    private final int mWorkspacePageIndicatorOverlapWorkspace;

    // Workspace icons
    public float iconScale;
    public int iconSizePx;
    public int iconTextSizePx;
    public int iconDrawablePaddingPx;
    private final int mIconDrawablePaddingOriginalPx;
    public boolean iconCenterVertically;

    public float cellScaleToFit;
    public int cellWidthPx;
    public int cellHeightPx;
    public int workspaceCellPaddingXPx;

    public int cellYPaddingPx = -1;

    // Folder
    public float folderLabelTextScale;
    public int folderLabelTextSizePx;
    public int folderFooterHeightPx;
    public int folderIconSizePx;
    public int folderIconOffsetYPx;

    // Folder content
    public Point folderCellLayoutBorderSpacePx;
    public int folderContentPaddingLeftRight;
    public int folderContentPaddingTop;

    // Folder cell
    public int folderCellWidthPx;
    public int folderCellHeightPx;

    // Folder child
    public int folderChildIconSizePx;
    public int folderChildTextSizePx;
    public int folderChildDrawablePaddingPx;

    // Hotseat
    public int numShownHotseatIcons;
    public int hotseatCellHeightPx;
    public final boolean areNavButtonsInline;
    // In portrait: size = height, in landscape: size = width
    public int hotseatBarSizePx;
    public int hotseatBarBottomSpacePx;
    public int hotseatBarEndOffset;
    public int hotseatQsbSpace;
    public int springLoadedHotseatBarTopMarginPx;
    // Start is the side next to the nav bar, end is the side next to the workspace
    public final int hotseatBarSidePaddingStartPx;
    public final int hotseatBarSidePaddingEndPx;
    public int hotseatQsbWidth; // only used when isQsbInline
    public final int hotseatQsbHeight;
    public final int hotseatQsbVisualHeight;
    private final int hotseatQsbShadowHeight;
    public int hotseatBorderSpace;
    private final int mMinHotseatIconSpacePx;
    private final int mMinHotseatQsbWidthPx;
    private final int mMaxHotseatIconSpacePx;
    public final int inlineNavButtonsEndSpacingPx;

    // Bottom sheets
    public int bottomSheetTopPadding;
    public int bottomSheetOpenDuration;
    public int bottomSheetCloseDuration;
    public float bottomSheetWorkspaceScale;
    public float bottomSheetDepth;

    // All apps
    public Point allAppsBorderSpacePx;
    public int allAppsShiftRange;
    public int allAppsTopPadding;
    public int allAppsOpenDuration;
    public int allAppsCloseDuration;
    public int allAppsCellHeightPx;
    public int allAppsCellWidthPx;
    public int allAppsIconSizePx;
    public int allAppsIconDrawablePaddingPx;
    public int allAppsLeftRightPadding;
    public int allAppsLeftRightMargin;
    public final int numShownAllAppsColumns;
    public float allAppsIconTextSizePx;
    private float allAppsCellHeightMultiplier;
    private boolean allAppsIconText;

    // Overview
    public int overviewTaskMarginPx;
    public int overviewTaskIconSizePx;
    public int overviewTaskIconDrawableSizePx;
    public int overviewTaskIconDrawableSizeGridPx;
    public int overviewTaskThumbnailTopMarginPx;
    public final int overviewActionsHeight;
    public final int overviewActionsTopMarginPx;
    public final int overviewActionsButtonSpacing;
    public int overviewPageSpacing;
    public int overviewRowSpacing;
    public int overviewGridSideMargin;

    // Split staging
    public int splitPlaceholderInset;

    // Widgets
    private final ViewScaleProvider mViewScaleProvider;

    // Drop Target
    public int dropTargetBarSizePx;
    public int dropTargetBarTopMarginPx;
    public int dropTargetBarBottomMarginPx;
    public int dropTargetDragPaddingPx;
    public int dropTargetTextSizePx;
    public int dropTargetHorizontalPaddingPx;
    public int dropTargetVerticalPaddingPx;
    public int dropTargetGapPx;
    public int dropTargetButtonWorkspaceEdgeGapPx;

    // Insets
    private final Rect mInsets = new Rect();
    public final Rect workspacePadding = new Rect();
    // Additional padding added to the widget inside its cellSpace. It is applied outside
    // the widgetView, such that the actual view size is same as the widget size.
    public final Rect widgetPadding = new Rect();

    // When true, nav bar is on the left side of the screen.
    private boolean mIsSeascape;

    // Notification dots
    public final DotRenderer mDotRendererWorkSpace;
    public final DotRenderer mDotRendererAllApps;

    // Taskbar
    public boolean isTaskbarPresent;
    // Whether Taskbar will inset the bottom of apps by taskbarSize.
    public boolean isTaskbarPresentInApps;
    public final int taskbarHeight;
    public final int stashedTaskbarHeight;
    public final int taskbarBottomMargin;
    public final int taskbarIconSize;
    // If true, used to layout taskbar in 3 button navigation mode.
    public final boolean startAlignTaskbar;
    public final boolean isTransientTaskbar;

    // DragController
    public int flingToDeleteThresholdVelocity;

    // Meminfo in overview
    public int memInfoMarginGesturePx;
    public int memInfoMarginTransientTaskbarPx;
    public int memInfoMarginThreeButtonPx;

    /** TODO: Once we fully migrate to staged split, remove "isMultiWindowMode" */
    DeviceProfile(Context context, InvariantDeviceProfile inv, Info info, WindowBounds windowBounds,
            SparseArray<DotRenderer> dotRendererCache, boolean isMultiWindowMode,
            boolean transposeLayoutWithOrientation, boolean isMultiDisplay, boolean isGestureMode,
            @NonNull final ViewScaleProvider viewScaleProvider,
            @NonNull final Consumer<DeviceProfile> dimensionOverrideProvider) {

        this.inv = inv;
        this.isLandscape = windowBounds.isLandscape();
        this.isMultiWindowMode = isMultiWindowMode;
        this.transposeLayoutWithOrientation = transposeLayoutWithOrientation;
        this.isMultiDisplay = isMultiDisplay;
        this.isGestureMode = isGestureMode;
        windowX = windowBounds.bounds.left;
        windowY = windowBounds.bounds.top;
        this.rotationHint = windowBounds.rotationHint;
        mInsets.set(windowBounds.insets);

       SharedPreferences prefs = LauncherPrefs.getPrefs(context);

        // TODO(b/241386436): shouldn't change any launcher behaviour
        mIsResponsiveGrid = inv.workspaceSpecsId != INVALID_RESOURCE_HANDLE
                && inv.allAppsSpecsId != INVALID_RESOURCE_HANDLE
                && inv.folderSpecsId != INVALID_RESOURCE_HANDLE
                && inv.hotseatSpecsId != INVALID_RESOURCE_HANDLE;

        mIsScalableGrid = inv.isScalable && !isVerticalBarLayout() && !isMultiWindowMode;
        // Determine device posture.
        mInfo = info;
        isTablet = info.isTablet(windowBounds);
        isPhone = !isTablet;
        isTwoPanels = isTablet && isMultiDisplay;
        isTaskbarPresent = isTablet && ApiWrapper.TASKBAR_DRAWN_IN_PROCESS;

        // Some more constants.
        context = getContext(context, info, isVerticalBarLayout() || (isTablet && isLandscape)
                        ? Configuration.ORIENTATION_LANDSCAPE
                        : Configuration.ORIENTATION_PORTRAIT,
                windowBounds);
        final Resources res = context.getResources();
        mMetrics = res.getDisplayMetrics();

        mIconSizeSteps = mIsResponsiveGrid ? new IconSizeSteps(res) : null;

        // Determine sizes.
        widthPx = windowBounds.bounds.width();
        heightPx = windowBounds.bounds.height();
        availableWidthPx = windowBounds.availableSize.x;
        availableHeightPx = windowBounds.availableSize.y;

        aspectRatio = ((float) Math.max(widthPx, heightPx)) / Math.min(widthPx, heightPx);
        if (isTwoPanels) {
            if (isLandscape) {
                mTypeIndex = INDEX_TWO_PANEL_LANDSCAPE;
            } else {
                mTypeIndex = INDEX_TWO_PANEL_PORTRAIT;
            }
        } else {
            if (isLandscape) {
                mTypeIndex = INDEX_LANDSCAPE;
            } else {
                mTypeIndex = INDEX_DEFAULT;
            }
        }

        isTransientTaskbar = DisplayController.isTransientTaskbar(context);
        if (!isTaskbarPresent) {
            taskbarIconSize = taskbarHeight = stashedTaskbarHeight = taskbarBottomMargin = 0;
            startAlignTaskbar = false;
        } else if (isTransientTaskbar) {
            float invTransientIconSizeDp = inv.transientTaskbarIconSize[mTypeIndex];
            taskbarIconSize = pxFromDp(invTransientIconSizeDp, mMetrics);
            taskbarHeight = Math.round((taskbarIconSize * ICON_VISIBLE_AREA_FACTOR)
                    + (2 * res.getDimensionPixelSize(R.dimen.transient_taskbar_padding)));
            stashedTaskbarHeight =
                    res.getDimensionPixelSize(R.dimen.transient_taskbar_stashed_height);
            taskbarBottomMargin =
                    res.getDimensionPixelSize(R.dimen.transient_taskbar_bottom_margin);
            startAlignTaskbar = false;
        } else {
            taskbarIconSize = pxFromDp(ResourcesCompat.getFloat(res, R.dimen.taskbar_icon_size),
                    mMetrics);
            taskbarHeight = res.getDimensionPixelSize(R.dimen.taskbar_size);
            stashedTaskbarHeight = res.getDimensionPixelSize(R.dimen.taskbar_stashed_size);
            taskbarBottomMargin = 0;
            startAlignTaskbar = inv.startAlignTaskbar[mTypeIndex];
        }

        allAppsCellHeightMultiplier =
                    (float) prefs.getInt(KEY_ROW_HEIGHT, 100) / 100F;
        allAppsIconText = prefs.getBoolean(InvariantDeviceProfile.KEY_SHOW_DRAWER_LABELS, true);

        edgeMarginPx = res.getDimensionPixelSize(R.dimen.dynamic_grid_edge_margin);
        workspaceContentScale = res.getFloat(R.dimen.workspace_content_scale);

        gridVisualizationPaddingX = res.getDimensionPixelSize(
                R.dimen.grid_visualization_horizontal_cell_spacing);
        gridVisualizationPaddingY = res.getDimensionPixelSize(
                R.dimen.grid_visualization_vertical_cell_spacing);

        bottomSheetTopPadding = mInsets.top // statusbar height
                + res.getDimensionPixelSize(R.dimen.bottom_sheet_extra_top_padding)
                + (isTablet ? 0 : edgeMarginPx); // phones need edgeMarginPx additional padding
        bottomSheetOpenDuration = res.getInteger(R.integer.config_bottomSheetOpenDuration);
        bottomSheetCloseDuration = res.getInteger(R.integer.config_bottomSheetCloseDuration);
        if (isTablet) {
            bottomSheetWorkspaceScale = workspaceContentScale;
            if (isMultiDisplay && !ENABLE_MULTI_DISPLAY_PARTIAL_DEPTH.get()) {
                // TODO(b/259893832): Revert to use maxWallpaperScale to calculate bottomSheetDepth
                // when screen recorder bug is fixed.
                bottomSheetDepth = 1f;
            } else {
                // The goal is to set wallpaper to zoom at workspaceContentScale when in AllApps.
                // When depth is 0, wallpaper zoom is set to maxWallpaperScale.
                // When depth is 1, wallpaper zoom is set to 1.
                // For depth to achieve zoom set to maxWallpaperScale * workspaceContentScale:
                float maxWallpaperScale = res.getFloat(R.dimen.config_wallpaperMaxScale);
                bottomSheetDepth = Utilities.mapToRange(maxWallpaperScale * workspaceContentScale,
                        maxWallpaperScale, 1f, 0f, 1f, LINEAR);
            }
        } else {
            bottomSheetWorkspaceScale = 1f;
            bottomSheetDepth = 0f;
        }

        folderLabelTextScale = res.getFloat(R.dimen.folder_label_text_scale);

        if (mIsScalableGrid && inv.folderStyle != INVALID_RESOURCE_HANDLE) {
            TypedArray folderStyle = context.obtainStyledAttributes(inv.folderStyle,
                    R.styleable.FolderStyle);
            // These are re-set in #updateFolderCellSize if the grid is not scalable
            folderCellHeightPx = folderStyle.getDimensionPixelSize(
                    R.styleable.FolderStyle_folderCellHeight, 0);
            folderCellWidthPx = folderStyle.getDimensionPixelSize(
                    R.styleable.FolderStyle_folderCellWidth, 0);

            folderContentPaddingTop = folderStyle.getDimensionPixelSize(
                    R.styleable.FolderStyle_folderTopPadding, 0);

            int gutter = folderStyle.getDimensionPixelSize(
                    R.styleable.FolderStyle_folderBorderSpace, 0);
            folderCellLayoutBorderSpacePx = new Point(gutter, gutter);
            folderFooterHeightPx = folderStyle.getDimensionPixelSize(
                    R.styleable.FolderStyle_folderFooterHeight, 0);
            folderStyle.recycle();
        } else if (!mIsResponsiveGrid) {
            folderCellLayoutBorderSpacePx = new Point(0, 0);
            folderFooterHeightPx = res.getDimensionPixelSize(R.dimen.folder_footer_height_default);
            folderContentPaddingTop = res.getDimensionPixelSize(R.dimen.folder_top_padding_default);
        }

        allAppsBorderSpacePx = new Point(
                pxFromDp(inv.allAppsBorderSpaces[mTypeIndex].x, mMetrics),
                pxFromDp(inv.allAppsBorderSpaces[mTypeIndex].y, mMetrics));
        setupAllAppsStyle(context);

        workspacePageIndicatorHeight = res.getDimensionPixelSize(
                R.dimen.workspace_page_indicator_height);
        mWorkspacePageIndicatorOverlapWorkspace =
                res.getDimensionPixelSize(R.dimen.workspace_page_indicator_overlap_workspace);

        TypedArray cellStyle;
        if (inv.cellStyle != INVALID_RESOURCE_HANDLE) {
            cellStyle = context.obtainStyledAttributes(inv.cellStyle,
                    R.styleable.CellStyle);
        } else {
            cellStyle = context.obtainStyledAttributes(R.style.CellStyleDefault,
                    R.styleable.CellStyle);
        }
        mIconDrawablePaddingOriginalPx = cellStyle.getDimensionPixelSize(
                R.styleable.CellStyle_iconDrawablePadding, 0);
        cellStyle.recycle();

        dropTargetBarSizePx = res.getDimensionPixelSize(R.dimen.dynamic_grid_drop_target_size);
        dropTargetBarTopMarginPx = res.getDimensionPixelSize(R.dimen.drop_target_top_margin);
        dropTargetBarBottomMarginPx = res.getDimensionPixelSize(R.dimen.drop_target_bottom_margin);
        dropTargetDragPaddingPx = res.getDimensionPixelSize(R.dimen.drop_target_drag_padding);
        dropTargetTextSizePx = res.getDimensionPixelSize(R.dimen.drop_target_text_size);
        dropTargetHorizontalPaddingPx = res.getDimensionPixelSize(
                R.dimen.drop_target_button_drawable_horizontal_padding);
        dropTargetVerticalPaddingPx = res.getDimensionPixelSize(
                R.dimen.drop_target_button_drawable_vertical_padding);
        dropTargetGapPx = res.getDimensionPixelSize(R.dimen.drop_target_button_gap);
        dropTargetButtonWorkspaceEdgeGapPx = res.getDimensionPixelSize(
                R.dimen.drop_target_button_workspace_edge_gap);

        workspaceSpringLoadedMinNextPageVisiblePx = res.getDimensionPixelSize(
                R.dimen.dynamic_grid_spring_loaded_min_next_space_visible);

        workspaceCellPaddingXPx = res.getDimensionPixelSize(R.dimen.dynamic_grid_cell_padding_x);

        hotseatQsbHeight = res.getDimensionPixelSize(R.dimen.qsb_widget_height);
        hotseatQsbShadowHeight = res.getDimensionPixelSize(R.dimen.qsb_shadow_height);
        hotseatQsbVisualHeight = hotseatQsbHeight - 2 * hotseatQsbShadowHeight;

        // Whether QSB might be inline in appropriate orientation (e.g. landscape).
        boolean canQsbInline = (isTwoPanels ? inv.inlineQsb[INDEX_TWO_PANEL_PORTRAIT]
                || inv.inlineQsb[INDEX_TWO_PANEL_LANDSCAPE]
                : inv.inlineQsb[INDEX_DEFAULT] || inv.inlineQsb[INDEX_LANDSCAPE])
                && hotseatQsbHeight > 0 && Utilities.showQSB(context);
        isQsbInline = inv.inlineQsb[mTypeIndex] && canQsbInline;

        areNavButtonsInline = isTaskbarPresent && !isGestureMode;
        numShownHotseatIcons =
                isTwoPanels ? inv.numDatabaseHotseatIcons : inv.numShownHotseatIcons;

        numShownAllAppsColumns =
                isTwoPanels ? inv.numDatabaseAllAppsColumns : inv.numAllAppsColumns;

        int hotseatBarBottomSpace = Utilities.showQSB(context)
                ? pxFromDp(inv.hotseatBarBottomSpace[mTypeIndex], mMetrics) : 0;
        int minQsbMargin = res.getDimensionPixelSize(R.dimen.min_qsb_margin);

        if (mIsResponsiveGrid) {
            HotseatSpecs hotseatSpecs =
                    HotseatSpecs.create(new ResourceHelper(context,
                            isTwoPanels ? inv.hotseatSpecsTwoPanelId : inv.hotseatSpecsId));
            mResponsiveHotseatSpec = hotseatSpecs.getCalculatedHeightSpec(heightPx);
            hotseatQsbSpace = mResponsiveHotseatSpec.getHotseatQsbSpace();
        } else {
            hotseatQsbSpace = Utilities.showQSB(context)
                    ? pxFromDp(inv.hotseatQsbSpace[mTypeIndex], mMetrics) : 0;
        }

        // Have a little space between the inset and the QSB
        if (Utilities.showQSB(context) &&
                (mInsets.bottom + minQsbMargin) > hotseatBarBottomSpace) {
            int availableSpace = hotseatQsbSpace - (mInsets.bottom - hotseatBarBottomSpace);

            // Only change the spaces if there is space
            if (availableSpace > 0) {
                // Make sure there is enough space between hotseat/QSB and QSB/navBar
                if (availableSpace < minQsbMargin * 2) {
                    minQsbMargin = availableSpace / 2;
                    hotseatQsbSpace = minQsbMargin;
                } else {
                    hotseatQsbSpace -= minQsbMargin;
                }
            }
            hotseatBarBottomSpacePx = mInsets.bottom + minQsbMargin;

        } else {
            hotseatBarBottomSpacePx = hotseatBarBottomSpace;
        }

        springLoadedHotseatBarTopMarginPx = res.getDimensionPixelSize(
                R.dimen.spring_loaded_hotseat_top_margin);
        hotseatBarSidePaddingEndPx =
                res.getDimensionPixelSize(R.dimen.dynamic_grid_hotseat_side_padding);
        // Add a bit of space between nav bar and hotseat in vertical bar layout.
        hotseatBarSidePaddingStartPx = isVerticalBarLayout() ? workspacePageIndicatorHeight : 0;
        updateHotseatSizes(pxFromDp(inv.iconSize[INDEX_DEFAULT], mMetrics));
        if (areNavButtonsInline && !isPhone) {
            inlineNavButtonsEndSpacingPx =
                    res.getDimensionPixelSize(inv.inlineNavButtonsEndSpacing);
            /*
             * 3 nav buttons +
             * Spacing between nav buttons +
             * Space at the end for contextual buttons
             */
            hotseatBarEndOffset = 3 * res.getDimensionPixelSize(R.dimen.taskbar_nav_buttons_size)
                    + 2 * res.getDimensionPixelSize(R.dimen.taskbar_button_space_inbetween)
                    + inlineNavButtonsEndSpacingPx;
        } else {
            inlineNavButtonsEndSpacingPx = 0;
            hotseatBarEndOffset = 0;
        }

        // Needs to be calculated after hotseatBarSizePx is correct,
        // for the available height to be correct
        if (mIsResponsiveGrid) {
            WorkspaceSpecs workspaceSpecs = WorkspaceSpecs.create(
                    new ResourceHelper(context,
                            isTwoPanels ? inv.workspaceSpecsTwoPanelId : inv.workspaceSpecsId));
            int availableResponsiveWidth =
                    availableWidthPx - (isVerticalBarLayout() ? hotseatBarSizePx : 0);
            int numColumns = getPanelCount() * inv.numColumns;
            // don't use availableHeightPx because it subtracts bottom padding,
            // but the workspace go behind it
            int availableResponsiveHeight =
                    heightPx - mInsets.top - (isVerticalBarLayout() ? 0 : hotseatBarSizePx);
            mResponsiveWidthSpec = workspaceSpecs.getCalculatedWidthSpec(numColumns,
                    availableResponsiveWidth);
            mResponsiveHeightSpec = workspaceSpecs.getCalculatedHeightSpec(inv.numRows,
                    availableResponsiveHeight);

            AllAppsSpecs allAppsSpecs = AllAppsSpecs.create(
                    new ResourceHelper(context,
                            isTwoPanels ? inv.allAppsSpecsTwoPanelId : inv.allAppsSpecsId));
            mAllAppsResponsiveWidthSpec = allAppsSpecs.getCalculatedWidthSpec(numColumns,
                    mResponsiveWidthSpec.getAvailableSpace(), mResponsiveWidthSpec);
            mAllAppsResponsiveHeightSpec = allAppsSpecs.getCalculatedHeightSpec(inv.numRows,
                    mResponsiveHeightSpec.getAvailableSpace(), mResponsiveHeightSpec);

            FolderSpecs folderSpecs = FolderSpecs.create(
                    new ResourceHelper(context,
                            isTwoPanels ? inv.folderSpecsTwoPanelId : inv.folderSpecsId));
            mResponsiveFolderWidthSpec = folderSpecs.getCalculatedWidthSpec(inv.numFolderColumns,
                    mResponsiveWidthSpec.getAvailableSpace(), mResponsiveWidthSpec);
            mResponsiveFolderHeightSpec = folderSpecs.getCalculatedHeightSpec(inv.numFolderRows,
                    mResponsiveHeightSpec.getAvailableSpace(), mResponsiveHeightSpec);
        }

        desiredWorkspaceHorizontalMarginPx = getHorizontalMarginPx(inv, res);
        desiredWorkspaceHorizontalMarginOriginalPx = desiredWorkspaceHorizontalMarginPx;

        overviewTaskMarginPx = res.getDimensionPixelSize(R.dimen.overview_task_margin);
        overviewTaskIconSizePx = res.getDimensionPixelSize(R.dimen.task_thumbnail_icon_size);
        overviewTaskIconDrawableSizePx =
                res.getDimensionPixelSize(R.dimen.task_thumbnail_icon_drawable_size);
        overviewTaskIconDrawableSizeGridPx =
                res.getDimensionPixelSize(R.dimen.task_thumbnail_icon_drawable_size_grid);
        overviewTaskThumbnailTopMarginPx = overviewTaskIconSizePx + overviewTaskMarginPx;
        // Don't add margin with floating search bar to minimize risk of overlapping.
        overviewActionsTopMarginPx = FeatureFlags.ENABLE_FLOATING_SEARCH_BAR.get() ? 0
                : res.getDimensionPixelSize(R.dimen.overview_actions_top_margin);
        overviewPageSpacing = res.getDimensionPixelSize(R.dimen.overview_page_spacing);
        overviewActionsButtonSpacing = res.getDimensionPixelSize(
                R.dimen.overview_actions_button_spacing);
        overviewActionsHeight = res.getDimensionPixelSize(R.dimen.overview_actions_height);
        overviewRowSpacing = res.getDimensionPixelSize(R.dimen.overview_grid_row_spacing);
        overviewGridSideMargin = res.getDimensionPixelSize(R.dimen.overview_grid_side_margin);

        splitPlaceholderInset = res.getDimensionPixelSize(R.dimen.split_placeholder_inset);

        memInfoMarginGesturePx = res.getDimensionPixelSize(
                R.dimen.meminfo_bottom_margin_gesture);
        memInfoMarginTransientTaskbarPx = res.getDimensionPixelSize(
                R.dimen.meminfo_bottom_margin_transient_taskbar);
        memInfoMarginThreeButtonPx = res.getDimensionPixelSize(
                R.dimen.meminfo_bottom_margin_three_button);

        // Calculate all of the remaining variables.
        extraSpace = updateAvailableDimensions(res);

        calculateAndSetWorkspaceVerticalPadding(context, inv, extraSpace);

        int cellLayoutPadding =
                isTwoPanels ? cellLayoutBorderSpacePx.x / 2 : res.getDimensionPixelSize(
                        R.dimen.cell_layout_padding);
        cellLayoutPaddingPx = new Rect(cellLayoutPadding, cellLayoutPadding, cellLayoutPadding,
                cellLayoutPadding);
        updateWorkspacePadding();

        // Folder scaling requires correct workspace paddings
        updateAvailableFolderCellDimensions(res);

        mMinHotseatIconSpacePx = res.getDimensionPixelSize(R.dimen.min_hotseat_icon_space);
        mMinHotseatQsbWidthPx = res.getDimensionPixelSize(R.dimen.min_hotseat_qsb_width);
        mMaxHotseatIconSpacePx = areNavButtonsInline
                ? res.getDimensionPixelSize(R.dimen.max_hotseat_icon_space) : Integer.MAX_VALUE;
        // Hotseat and QSB width depends on updated cellSize and workspace padding
        recalculateHotseatWidthAndBorderSpace();

        if (mIsResponsiveGrid && isVerticalBarLayout()) {
            hotseatBorderSpace = cellLayoutBorderSpacePx.y;
        }

        // AllApps height calculation depends on updated cellSize
        if (isTablet) {
            int collapseHandleHeight =
                    res.getDimensionPixelOffset(R.dimen.bottom_sheet_handle_area_height);
            int contentHeight = heightPx - collapseHandleHeight - hotseatQsbHeight;
            int targetContentHeight = (int) (allAppsCellHeightPx * ALL_APPS_TABLET_MAX_ROWS);
            allAppsTopPadding = Math.max(mInsets.top, contentHeight - targetContentHeight);
            allAppsShiftRange = heightPx - allAppsTopPadding;
        } else {
            allAppsTopPadding = 0;
            allAppsShiftRange =
                    res.getDimensionPixelSize(R.dimen.all_apps_starting_vertical_translate);
        }
        allAppsOpenDuration = res.getInteger(R.integer.config_allAppsOpenDuration);
        allAppsCloseDuration = res.getInteger(R.integer.config_allAppsCloseDuration);

        flingToDeleteThresholdVelocity = res.getDimensionPixelSize(
                R.dimen.drag_flingToDeleteMinVelocity);

        mViewScaleProvider = viewScaleProvider;

        dimensionOverrideProvider.accept(this);

        // This is done last, after iconSizePx is calculated above.
        mDotRendererWorkSpace = createDotRenderer(context, iconSizePx, dotRendererCache);
        mDotRendererAllApps = createDotRenderer(context, allAppsIconSizePx, dotRendererCache);
    }

    private static DotRenderer createDotRenderer(
            @NonNull Context context, int size, @NonNull SparseArray<DotRenderer> cache) {
        DotRenderer renderer = cache.get(size);
        if (renderer == null) {
            renderer = new DotRenderer(size, getShapePath(context, DEFAULT_DOT_SIZE),
                    DEFAULT_DOT_SIZE);
            cache.put(size, renderer);
        }
        return renderer;
    }

    /**
     * QSB width is always calculated because when in 3 button nav the width doesn't follow the
     * width of the hotseat.
     */
    private int calculateQsbWidth(int hotseatBorderSpace) {
        if (isQsbInline) {
            int columns = getPanelCount() * inv.numColumns;
            return getIconToIconWidthForColumns(columns)
                    - iconSizePx * numShownHotseatIcons
                    - hotseatBorderSpace * numShownHotseatIcons;
        } else {
            int columns = inv.hotseatColumnSpan[mTypeIndex];
            return getIconToIconWidthForColumns(columns)
                    - hotseatBorderSpace;
        }
    }

    private int getIconToIconWidthForColumns(int columns) {
        return columns * getCellSize().x
                + (columns - 1) * cellLayoutBorderSpacePx.x
                - getCellHorizontalSpace();
    }

    private int getHorizontalMarginPx(InvariantDeviceProfile idp, Resources res) {
        if (mIsResponsiveGrid) {
            return mResponsiveWidthSpec.getStartPaddingPx();
        }

        if (isVerticalBarLayout()) {
            return 0;
        }

        return mIsScalableGrid
                ? pxFromDp(idp.horizontalMargin[mTypeIndex], mMetrics)
                : res.getDimensionPixelSize(R.dimen.dynamic_grid_left_right_margin);
    }

    private void calculateAndSetWorkspaceVerticalPadding(Context context,
            InvariantDeviceProfile inv,
            int extraSpace) {
        if (mIsResponsiveGrid) {
            workspaceTopPadding = mResponsiveHeightSpec.getStartPaddingPx();
            workspaceBottomPadding = mResponsiveHeightSpec.getEndPaddingPx();
        } else if (mIsScalableGrid && inv.devicePaddingId != INVALID_RESOURCE_HANDLE) {
            // Paddings were created assuming no scaling, so we first unscale the extra space.
            int unscaledExtraSpace = (int) (extraSpace / cellScaleToFit);
            DevicePaddings devicePaddings = new DevicePaddings(context, inv.devicePaddingId);
            DevicePadding padding = devicePaddings.getDevicePadding(unscaledExtraSpace);
            maxEmptySpace = padding.getMaxEmptySpacePx();

            int paddingWorkspaceTop = padding.getWorkspaceTopPadding(unscaledExtraSpace);
            int paddingWorkspaceBottom = padding.getWorkspaceBottomPadding(unscaledExtraSpace);

            workspaceTopPadding = Math.round(paddingWorkspaceTop * cellScaleToFit);
            workspaceBottomPadding = Math.round(paddingWorkspaceBottom * cellScaleToFit);
        }
    }

    /** Updates hotseatCellHeightPx and hotseatBarSizePx */
    private void updateHotseatSizes(int hotseatIconSizePx) {
        // Ensure there is enough space for folder icons, which have a slightly larger radius.
        hotseatCellHeightPx = getIconSizeWithOverlap(hotseatIconSizePx);

        if (isVerticalBarLayout()) {
            hotseatBarSizePx = hotseatIconSizePx + hotseatBarSidePaddingStartPx
                    + hotseatBarSidePaddingEndPx;
        } else if (isQsbInline) {
            hotseatBarSizePx = Math.max(hotseatIconSizePx, hotseatQsbVisualHeight)
                    + hotseatBarBottomSpacePx;
        } else {
            hotseatBarSizePx = hotseatIconSizePx
                    + hotseatQsbSpace
                    + hotseatQsbVisualHeight
                    + hotseatBarBottomSpacePx;
        }
    }

    /**
     * Calculates the width of the hotseat, changing spaces between the icons and removing icons if
     * necessary.
     */
    public void recalculateHotseatWidthAndBorderSpace() {
        int columns = inv.hotseatColumnSpan[mTypeIndex];
        float hotseatWidthPx = getIconToIconWidthForColumns(columns);
        hotseatBorderSpace = calculateHotseatBorderSpace(hotseatWidthPx, /* numExtraBorder= */ 0);
        hotseatQsbWidth = calculateQsbWidth(hotseatBorderSpace);
        // Spaces should be correct when the nav buttons are not inline
        if (!areNavButtonsInline) {
            return;
        }

        // The side space with inline buttons should be what is defined in InvariantDeviceProfile
        int sideSpacePx = inlineNavButtonsEndSpacingPx;
        int maxHotseatWidthPx = availableWidthPx - sideSpacePx - hotseatBarEndOffset;
        int maxHotseatIconsWidthPx = maxHotseatWidthPx - (isQsbInline ? hotseatQsbWidth : 0);
        hotseatBorderSpace = calculateHotseatBorderSpace(maxHotseatIconsWidthPx,
                (isQsbInline ? 1 : 0) + /* border between nav buttons and first icon */ 1);

        if (hotseatBorderSpace >= mMinHotseatIconSpacePx) {
            return;
        }

        // Border space can't be less than the minimum
        hotseatBorderSpace = mMinHotseatIconSpacePx;
        int requiredWidth = getHotseatRequiredWidth();

        // If there is an inline qsb, change its size
        if (isQsbInline) {
            hotseatQsbWidth -= requiredWidth - maxHotseatWidthPx;
            if (hotseatQsbWidth >= mMinHotseatQsbWidthPx) {
                return;
            }

            // QSB can't be less than the minimum
            hotseatQsbWidth = mMinHotseatQsbWidthPx;
        }

        maxHotseatIconsWidthPx = maxHotseatWidthPx - (isQsbInline ? hotseatQsbWidth : 0);

        // If it still doesn't fit, start removing icons
        do {
            numShownHotseatIcons--;
            hotseatBorderSpace = calculateHotseatBorderSpace(maxHotseatIconsWidthPx,
                    (isQsbInline ? 1 : 0) + /* border between nav buttons and first icon */ 1);
        } while (hotseatBorderSpace < mMinHotseatIconSpacePx && numShownHotseatIcons > 1);
    }

    private Point getCellLayoutBorderSpace(InvariantDeviceProfile idp) {
        return getCellLayoutBorderSpace(idp, 1f);
    }

    private Point getCellLayoutBorderSpace(InvariantDeviceProfile idp, float scale) {
        int horizontalSpacePx = 0;
        int verticalSpacePx = 0;

        if (mIsResponsiveGrid) {
            horizontalSpacePx = mResponsiveWidthSpec.getGutterPx();
            verticalSpacePx = mResponsiveHeightSpec.getGutterPx();
        } else if (mIsScalableGrid) {
            horizontalSpacePx = pxFromDp(idp.borderSpaces[mTypeIndex].x, mMetrics, scale);
            verticalSpacePx = pxFromDp(idp.borderSpaces[mTypeIndex].y, mMetrics, scale);
        }

        return new Point(horizontalSpacePx, verticalSpacePx);
    }

    public Info getDisplayInfo() {
        return mInfo;
    }

    public Builder toBuilder(Context context) {
        WindowBounds bounds = new WindowBounds(
                widthPx, heightPx, availableWidthPx, availableHeightPx, rotationHint);
        bounds.bounds.offsetTo(windowX, windowY);
        bounds.insets.set(mInsets);

        SparseArray<DotRenderer> dotRendererCache = new SparseArray<>();
        dotRendererCache.put(iconSizePx, mDotRendererWorkSpace);
        dotRendererCache.put(allAppsIconSizePx, mDotRendererAllApps);

        return new Builder(context, inv, mInfo)
                .setWindowBounds(bounds)
                .setIsMultiDisplay(isMultiDisplay)
                .setMultiWindowMode(isMultiWindowMode)
                .setDotRendererCache(dotRendererCache)
                .setGestureMode(isGestureMode);
    }

    public DeviceProfile copy(Context context) {
        return toBuilder(context).build();
    }

    /**
     * TODO: Move this to the builder as part of setMultiWindowMode
     */
    public DeviceProfile getMultiWindowProfile(Context context, WindowBounds windowBounds) {
        DeviceProfile profile = toBuilder(context)
                .setWindowBounds(windowBounds)
                .setMultiWindowMode(true)
                .build();

        // We use these scales to measure and layout the widgets using their full invariant profile
        // sizes and then draw them scaled and centered to fit in their multi-window mode cellspans.
        float appWidgetScaleX = (float) profile.getCellSize().x / getCellSize().x;
        float appWidgetScaleY = (float) profile.getCellSize().y / getCellSize().y;
        if (appWidgetScaleX != 1 || appWidgetScaleY != 1) {
            final PointF p = new PointF(appWidgetScaleX, appWidgetScaleY);
            profile = profile.toBuilder(context)
                    .setViewScaleProvider(i -> p)
                    .build();
        }

        profile.hideWorkspaceLabelsIfNotEnoughSpace();

        return profile;
    }

    /**
     * Checks if there is enough space for labels on the workspace.
     * If there is not, labels on the Workspace are hidden.
     * It is important to call this method after the All Apps variables have been set.
     */
    private void hideWorkspaceLabelsIfNotEnoughSpace() {
        float iconTextHeight = Utilities.calculateTextHeight(iconTextSizePx);
        float workspaceCellPaddingY = getCellSize().y - iconSizePx - iconDrawablePaddingPx
                - iconTextHeight;

        if (mIsResponsiveGrid) {
            // Hide text only if doesn't fit inside the cell for responsive grid
            if (workspaceCellPaddingY < 0) {
                iconTextSizePx = 0;
                iconDrawablePaddingPx = 0;
                int iconSizeWithOverlap = getIconSizeWithOverlap(iconSizePx);
                cellYPaddingPx = Math.max(0, getCellSize().y - iconSizeWithOverlap) / 2;
                autoResizeAllAppsCells();
            }

            return;
        }

        // We want enough space so that the text is closer to its corresponding icon.
        if (workspaceCellPaddingY < iconTextHeight) {
            iconTextSizePx = 0;
            iconDrawablePaddingPx = 0;
            cellHeightPx = getIconSizeWithOverlap(iconSizePx);
            autoResizeAllAppsCells();
        }
    }

    /**
     * Returns the amount of extra (or unused) vertical space.
     */
    private int updateAvailableDimensions(Resources res) {
        float invIconSizeDp = inv.iconSize[mTypeIndex];
        float invIconTextSizeSp = inv.iconTextSize[mTypeIndex];
        iconSizePx = Math.max(1, pxFromDp(invIconSizeDp, mMetrics));
        iconTextSizePx = pxFromSp(invIconTextSizeSp, mMetrics);
        iconCenterVertically = mIsScalableGrid || mIsResponsiveGrid;

        updateIconSize(1f, res);
        updateWorkspacePadding();

        if (mIsResponsiveGrid) {
            return 0;
        }

        // Check to see if the icons fit within the available height.
        float usedHeight = getCellLayoutHeightSpecification();
        final int maxHeight = getCellLayoutHeight();
        float extraHeight = Math.max(0, maxHeight - usedHeight);
        float scaleY = maxHeight / usedHeight;
        boolean shouldScale = scaleY < 1f;

        float scaleX = 1f;
        if (mIsScalableGrid) {
            // We scale to fit the cellWidth and cellHeight in the available space.
            // The benefit of scalable grids is that we can get consistent aspect ratios between
            // devices.
            float usedWidth =
                    getCellLayoutWidthSpecification() + (desiredWorkspaceHorizontalMarginPx * 2);
            // We do not subtract padding here, as we also scale the workspace padding if needed.
            scaleX = availableWidthPx / usedWidth;
            shouldScale = true;
        }

        if (shouldScale) {
            float scale = Math.min(scaleX, scaleY);
            updateIconSize(scale, res);
            extraHeight = Math.max(0, maxHeight - getCellLayoutHeightSpecification());
        }

        return Math.round(extraHeight);
    }

    private int getCellLayoutHeightSpecification() {
        return (cellHeightPx * inv.numRows) + (cellLayoutBorderSpacePx.y * (inv.numRows - 1))
                + cellLayoutPaddingPx.top + cellLayoutPaddingPx.bottom;
    }

    private int getCellLayoutWidthSpecification() {
        int numColumns = getPanelCount() * inv.numColumns;
        return (cellWidthPx * numColumns) + (cellLayoutBorderSpacePx.x * (numColumns - 1))
                + cellLayoutPaddingPx.left + cellLayoutPaddingPx.right;
    }

    private int getNormalizedIconDrawablePadding() {
        // TODO(b/235886078): workaround needed because of this bug
        // Icons are 10% larger on XML than their visual size,
        // so remove that extra space to get labels closer to the correct padding
        int iconVisibleSizePx = (int) Math.round(ICON_VISIBLE_AREA_FACTOR * iconSizePx);
        return Math.max(0, mIconDrawablePaddingOriginalPx - ((iconSizePx - iconVisibleSizePx) / 2));
    }

    private int getNormalizedFolderChildDrawablePaddingPx(int textHeight) {
        // TODO(b/235886078): workaround needed because of this bug
        // Icons are 10% larger on XML than their visual size,
        // so remove that extra space to get labels closer to the correct padding
        int drawablePadding = (folderCellHeightPx - folderChildIconSizePx - textHeight) / 3;

        int iconVisibleSizePx = Math.round(ICON_VISIBLE_AREA_FACTOR * folderChildIconSizePx);
        int iconSizeDiff = folderChildIconSizePx - iconVisibleSizePx;
        return Math.max(0, drawablePadding - iconSizeDiff / 2);
    }

    private int getIconSizeWithOverlap(int iconSize) {
        return (int) Math.ceil(iconSize * ICON_OVERLAP_FACTOR);
    }

    /**
     * Updating the iconSize affects many aspects of the launcher layout, such as: iconSizePx,
     * iconTextSizePx, iconDrawablePaddingPx, cellWidth/Height, allApps* variants,
     * hotseat sizes, workspaceSpringLoadedShrinkFactor, folderIconSizePx, and folderIconOffsetYPx.
     */
    public void updateIconSize(float scale, Resources res) {
        // Icon scale should never exceed 1, otherwise pixellation may occur.
        iconScale = Math.min(1f, scale);
        cellScaleToFit = scale;

        // Workspace
        final boolean isVerticalLayout = isVerticalBarLayout();
        cellLayoutBorderSpacePx = getCellLayoutBorderSpace(inv, scale);

        if (mIsResponsiveGrid) {
            cellWidthPx = mResponsiveWidthSpec.getCellSizePx();
            cellHeightPx = mResponsiveHeightSpec.getCellSizePx();

            if (cellWidthPx < iconSizePx) {
                // get a smaller icon size
                iconSizePx = mIconSizeSteps.getIconSmallerThan(cellWidthPx);
            }

            iconDrawablePaddingPx = getNormalizedIconDrawablePadding();
            int iconTextHeight = Utilities.calculateTextHeight(iconTextSizePx);
            int cellContentHeight = iconSizePx + iconDrawablePaddingPx + iconTextHeight;

            while (iconSizePx > mIconSizeSteps.minimumIconSize()
                    && cellContentHeight > cellHeightPx) {
                iconDrawablePaddingPx -= cellContentHeight - cellHeightPx;
                if (iconDrawablePaddingPx < 0) {
                    // get a smaller icon size
                    iconSizePx = mIconSizeSteps.getNextLowerIconSize(iconSizePx);
                    iconDrawablePaddingPx = getNormalizedIconDrawablePadding();
                }
                // calculate new cellContentHeight
                cellContentHeight = iconSizePx + iconDrawablePaddingPx + iconTextHeight;
            }

            cellYPaddingPx = Math.max(0, cellHeightPx - cellContentHeight) / 2;
        } else if (mIsScalableGrid) {
            iconDrawablePaddingPx = (int) (getNormalizedIconDrawablePadding() * iconScale);
            cellWidthPx = pxFromDp(inv.minCellSize[mTypeIndex].x, mMetrics, scale);
            cellHeightPx = pxFromDp(inv.minCellSize[mTypeIndex].y, mMetrics, scale);

            if (cellWidthPx < iconSizePx) {
                // If cellWidth no longer fit iconSize, reduce borderSpace to make cellWidth bigger.
                int numColumns = getPanelCount() * inv.numColumns;
                int numBorders = numColumns - 1;
                int extraWidthRequired = (iconSizePx - cellWidthPx) * numColumns;
                if (cellLayoutBorderSpacePx.x * numBorders >= extraWidthRequired) {
                    cellWidthPx = iconSizePx;
                    cellLayoutBorderSpacePx.x -= extraWidthRequired / numBorders;
                } else {
                    // If it still doesn't fit, set borderSpace to 0 and distribute the space for
                    // cellWidth, and reduce iconSize.
                    cellWidthPx = (cellWidthPx * numColumns
                            + cellLayoutBorderSpacePx.x * numBorders) / numColumns;
                    iconSizePx = Math.min(iconSizePx, cellWidthPx);
                    cellLayoutBorderSpacePx.x = 0;
                }
            }

            int cellTextAndPaddingHeight =
                    iconDrawablePaddingPx + Utilities.calculateTextHeight(iconTextSizePx);
            int cellContentHeight = iconSizePx + cellTextAndPaddingHeight;
            if (cellHeightPx < cellContentHeight) {
                // If cellHeight no longer fit iconSize, reduce borderSpace to make cellHeight
                // bigger.
                int numBorders = inv.numRows - 1;
                int extraHeightRequired = (cellContentHeight - cellHeightPx) * inv.numRows;
                if (cellLayoutBorderSpacePx.y * numBorders >= extraHeightRequired) {
                    cellHeightPx = cellContentHeight;
                    cellLayoutBorderSpacePx.y -= extraHeightRequired / numBorders;
                } else {
                    // If it still doesn't fit, set borderSpace to 0 to recover space.
                    cellHeightPx = (cellHeightPx * inv.numRows
                            + cellLayoutBorderSpacePx.y * numBorders) / inv.numRows;
                    cellLayoutBorderSpacePx.y = 0;
                    // Reduce iconDrawablePaddingPx to make cellContentHeight smaller.
                    int cellContentWithoutPadding = cellContentHeight - iconDrawablePaddingPx;
                    if (cellContentWithoutPadding <= cellHeightPx) {
                        iconDrawablePaddingPx = cellContentHeight - cellHeightPx;
                    } else {
                        // If it still doesn't fit, set iconDrawablePaddingPx to 0 to recover space,
                        // then proportional reduce iconSizePx and iconTextSizePx to fit.
                        iconDrawablePaddingPx = 0;
                        float ratio = cellHeightPx / (float) cellContentWithoutPadding;
                        iconSizePx = (int) (iconSizePx * ratio);
                        iconTextSizePx = (int) (iconTextSizePx * ratio);
                    }
                    cellTextAndPaddingHeight =
                            iconDrawablePaddingPx + Utilities.calculateTextHeight(iconTextSizePx);
                }
                cellContentHeight = iconSizePx + cellTextAndPaddingHeight;
            }
            cellYPaddingPx = Math.max(0, cellHeightPx - cellContentHeight) / 2;
            desiredWorkspaceHorizontalMarginPx =
                    (int) (desiredWorkspaceHorizontalMarginOriginalPx * scale);
        } else {
            iconDrawablePaddingPx = (int) (getNormalizedIconDrawablePadding() * iconScale);
            cellWidthPx = iconSizePx + iconDrawablePaddingPx;
            cellHeightPx = getIconSizeWithOverlap(iconSizePx)
                    + iconDrawablePaddingPx
                    + Utilities.calculateTextHeight(iconTextSizePx);
            int cellPaddingY = (getCellSize().y - cellHeightPx) / 2;
            if (iconDrawablePaddingPx > cellPaddingY && !isVerticalLayout
                    && !isMultiWindowMode) {
                // Ensures that the label is closer to its corresponding icon. This is not an issue
                // with vertical bar layout or multi-window mode since the issue is handled
                // separately with their calls to {@link #adjustToHideWorkspaceLabels}.
                cellHeightPx -= (iconDrawablePaddingPx - cellPaddingY);
                iconDrawablePaddingPx = cellPaddingY;
            }
        }

        // All apps
        if (mIsResponsiveGrid) {
            updateAllAppsWithResponsiveMeasures();
        } else {
            updateAllAppsIconSize(scale, res);
        }
        if (!allAppsIconText) {
            int cellLayoutHorizontalPadding =
                    (cellLayoutPaddingPx.left + cellLayoutPaddingPx.right) / 2;
            int leftRightPadding = desiredWorkspaceHorizontalMarginPx + cellLayoutHorizontalPadding;
            int drawerWidth = availableWidthPx - leftRightPadding * 2;
            allAppsCellHeightPx = (int) (drawerWidth / inv.numAllAppsColumns * allAppsCellHeightMultiplier);
            allAppsIconDrawablePaddingPx = 0;
        }
        int cellContentHeight = allAppsIconSizePx + allAppsBorderSpacePx.y
                + (allAppsIconText ? Utilities.calculateTextHeight(allAppsIconTextSizePx) : 0);
        if (allAppsCellHeightPx < cellContentHeight) {
            // Increase allAppsCellHeight to fit its content.
            allAppsCellHeightPx = cellContentHeight;
        }
        updateAllAppsContainerWidth();
        if (isVerticalBarLayout()) {
            hideWorkspaceLabelsIfNotEnoughSpace();
        }

        updateHotseatSizes(iconSizePx);

        // Folder icon
        folderIconSizePx = IconNormalizer.getNormalizedCircleSize(iconSizePx);
        folderIconOffsetYPx = (iconSizePx - folderIconSizePx) / 2;

        // Update widget padding:
        float minSpacing = pxFromDp(MIN_WIDGET_PADDING_DP, mMetrics);
        if (cellLayoutBorderSpacePx.x < minSpacing
                || cellLayoutBorderSpacePx.y < minSpacing) {
            widgetPadding.left = widgetPadding.right =
                    Math.round(Math.max(0, minSpacing - cellLayoutBorderSpacePx.x));
            widgetPadding.top = widgetPadding.bottom =
                    Math.round(Math.max(0, minSpacing - cellLayoutBorderSpacePx.y));
        } else {
            widgetPadding.setEmpty();
        }
    }

    /**
     * This method calculates the space between the icons to achieve a certain width.
     */
    private int calculateHotseatBorderSpace(float hotseatWidthPx, int numExtraBorder) {
        int numBorders = (numShownHotseatIcons - 1 + numExtraBorder);
        if (numBorders <= 0) return 0;

        float hotseatIconsTotalPx = iconSizePx * numShownHotseatIcons;
        int hotseatBorderSpacePx = (int) (hotseatWidthPx - hotseatIconsTotalPx) / numBorders;
        return Math.min(hotseatBorderSpacePx, mMaxHotseatIconSpacePx);
    }

    /**
     * Updates the iconSize for allApps* variants.
     */
    private void updateAllAppsIconSize(float scale, Resources res) {
        allAppsBorderSpacePx = new Point(
                pxFromDp(inv.allAppsBorderSpaces[mTypeIndex].x, mMetrics, scale),
                pxFromDp(inv.allAppsBorderSpaces[mTypeIndex].y, mMetrics, scale));
        // AllApps cells don't have real space between cells,
        // so we add the border space to the cell height
        int baseCellHeight = pxFromDp(inv.allAppsCellSize[mTypeIndex].y, mMetrics)
                + allAppsBorderSpacePx.y;
        allAppsCellHeightPx = (int) (baseCellHeight * allAppsCellHeightMultiplier);
        // but width is just the cell,
        // the border is added in #updateAllAppsContainerWidth
        if (mIsScalableGrid) {
            allAppsIconSizePx = pxFromDp(inv.allAppsIconSize[mTypeIndex], mMetrics);
            allAppsIconTextSizePx = pxFromSp(inv.allAppsIconTextSize[mTypeIndex], mMetrics);
            allAppsIconDrawablePaddingPx = getNormalizedIconDrawablePadding();
            allAppsCellWidthPx = pxFromDp(inv.allAppsCellSize[mTypeIndex].x, mMetrics, scale);

            if (allAppsCellWidthPx < allAppsIconSizePx) {
                // If allAppsCellWidth no longer fit allAppsIconSize, reduce allAppsBorderSpace to
                // make allAppsCellWidth bigger.
                int numBorders = inv.numAllAppsColumns - 1;
                int extraWidthRequired =
                        (allAppsIconSizePx - allAppsCellWidthPx) * inv.numAllAppsColumns;
                if (allAppsBorderSpacePx.x * numBorders >= extraWidthRequired) {
                    allAppsCellWidthPx = allAppsIconSizePx;
                    allAppsBorderSpacePx.x -= extraWidthRequired / numBorders;
                } else {
                    // If it still doesn't fit, set allAppsBorderSpace to 0 and distribute the space
                    // for allAppsCellWidth, and reduce allAppsIconSize.
                    allAppsCellWidthPx = (allAppsCellWidthPx * inv.numAllAppsColumns
                            + allAppsBorderSpacePx.x * numBorders) / inv.numAllAppsColumns;
                    allAppsIconSizePx = Math.min(allAppsIconSizePx, allAppsCellWidthPx);
                    allAppsBorderSpacePx.x = 0;
                }
            }
        } else {
            float invIconSizeDp = inv.allAppsIconSize[mTypeIndex];
            float invIconTextSizeSp = inv.allAppsIconTextSize[mTypeIndex];
            allAppsIconSizePx = Math.max(1, pxFromDp(invIconSizeDp, mMetrics, scale));
            allAppsIconTextSizePx = (int) (pxFromSp(invIconTextSizeSp, mMetrics) * scale);
            allAppsIconDrawablePaddingPx =
                    res.getDimensionPixelSize(R.dimen.all_apps_icon_drawable_padding);
            allAppsCellWidthPx = allAppsIconSizePx + (2 * allAppsIconDrawablePaddingPx);
        }
    }

    private void updateAllAppsWithResponsiveMeasures() {
        allAppsIconSizePx = iconSizePx;
        allAppsIconTextSizePx = iconTextSizePx;
        allAppsIconDrawablePaddingPx = iconDrawablePaddingPx;

        allAppsBorderSpacePx = new Point(
                mAllAppsResponsiveWidthSpec.getGutterPx(),
                mAllAppsResponsiveHeightSpec.getGutterPx()
        );
        allAppsCellHeightPx = mAllAppsResponsiveHeightSpec.getCellSizePx()
                + mAllAppsResponsiveHeightSpec.getGutterPx();
        allAppsCellWidthPx = mAllAppsResponsiveWidthSpec.getCellSizePx();
        allAppsLeftRightPadding = mAllAppsResponsiveWidthSpec.getStartPaddingPx();
    }

    /**
     * Re-computes the all-apps cell size to be independent of workspace
     */
    public void autoResizeAllAppsCells() {
        int textHeight = Utilities.calculateTextHeight(allAppsIconTextSizePx);
        int topBottomPadding = textHeight;
        int baseCellHeight = allAppsIconSizePx + allAppsIconDrawablePaddingPx
                + textHeight + (topBottomPadding * 2);
        allAppsCellHeightPx = (int) (baseCellHeight * allAppsCellHeightMultiplier);
        if (!allAppsIconText) {
            int cellLayoutHorizontalPadding =
                    (cellLayoutPaddingPx.left + cellLayoutPaddingPx.right) / 2;
            int leftRightPadding = desiredWorkspaceHorizontalMarginPx + cellLayoutHorizontalPadding;
            int drawerWidth = availableWidthPx - leftRightPadding * 2;
            allAppsCellHeightPx = (int) (drawerWidth / inv.numAllAppsColumns * allAppsCellHeightMultiplier);
            allAppsIconDrawablePaddingPx = 0;
        }
    }

    private void updateAllAppsContainerWidth() {
        int cellLayoutHorizontalPadding =
                (cellLayoutPaddingPx.left + cellLayoutPaddingPx.right) / 2;
        if (isTablet) {
            int usedWidth = (allAppsCellWidthPx * numShownAllAppsColumns)
                    + (allAppsBorderSpacePx.x * (numShownAllAppsColumns - 1))
                    + allAppsLeftRightPadding * 2;
            allAppsLeftRightMargin = Math.max(1, (availableWidthPx - usedWidth) / 2);
        } else {
            allAppsLeftRightPadding =
                    Math.max(0, desiredWorkspaceHorizontalMarginPx + cellLayoutHorizontalPadding
                            - (allAppsBorderSpacePx.x / 2));
        }
    }

    private void setupAllAppsStyle(Context context) {
        TypedArray allAppsStyle = context.obtainStyledAttributes(
                inv.allAppsStyle != INVALID_RESOURCE_HANDLE ? inv.allAppsStyle
                        : R.style.AllAppsStyleDefault, R.styleable.AllAppsStyle);

        allAppsLeftRightPadding = allAppsStyle.getDimensionPixelSize(
                R.styleable.AllAppsStyle_horizontalPadding, 0);
        allAppsStyle.recycle();
    }

    private void updateAvailableFolderCellDimensions(Resources res) {
        updateFolderCellSize(1f, res);

        // Responsive grid doesn't need to scale the folder
        if (mIsResponsiveGrid) return;

        // For usability we can't have the folder use the whole width of the screen
        Point totalWorkspacePadding = getTotalWorkspacePadding();

        // Check if the folder fit within the available height.
        float contentUsedHeight = folderCellHeightPx * inv.numFolderRows
                + ((inv.numFolderRows - 1) * folderCellLayoutBorderSpacePx.y)
                + folderFooterHeightPx
                + folderContentPaddingTop;
        int contentMaxHeight = availableHeightPx - totalWorkspacePadding.y;
        float scaleY = contentMaxHeight / contentUsedHeight;

        // Check if the folder fit within the available width.
        float contentUsedWidth = folderCellWidthPx * inv.numFolderColumns
                + ((inv.numFolderColumns - 1) * folderCellLayoutBorderSpacePx.x)
                + folderContentPaddingLeftRight * 2;
        int contentMaxWidth = availableWidthPx - totalWorkspacePadding.x;
        float scaleX = contentMaxWidth / contentUsedWidth;

        float scale = Math.min(scaleX, scaleY);
        if (scale < 1f) {
            updateFolderCellSize(scale, res);
        }
    }

    private void updateFolderCellSize(float scale, Resources res) {
        float invIconSizeDp = inv.iconSize[mTypeIndex];
        folderChildIconSizePx = Math.max(1, pxFromDp(invIconSizeDp, mMetrics, scale));
        folderChildTextSizePx = pxFromSp(inv.iconTextSize[mTypeIndex], mMetrics, scale);
        folderLabelTextSizePx = Math.max(pxFromSp(MIN_FOLDER_TEXT_SIZE_SP, mMetrics, scale),
                (int) (folderChildTextSizePx * folderLabelTextScale));

        int textHeight = Utilities.calculateTextHeight(folderChildTextSizePx);

        if (mIsResponsiveGrid) {
            folderCellWidthPx = mResponsiveFolderWidthSpec.getCellSizePx();

            // Height
            folderCellHeightPx = mResponsiveFolderHeightSpec.getCellSizePx();
            folderContentPaddingTop = mResponsiveFolderHeightSpec.getStartPaddingPx();
            folderFooterHeightPx = mResponsiveFolderHeightSpec.getEndPaddingPx();

            folderCellLayoutBorderSpacePx = new Point(mResponsiveFolderWidthSpec.getGutterPx(),
                    mResponsiveFolderHeightSpec.getGutterPx());

            folderContentPaddingLeftRight = mResponsiveFolderWidthSpec.getStartPaddingPx();

            // Reduce icon width if it's wider than the expected folder cell width
            if (folderCellWidthPx < folderChildIconSizePx) {
                folderChildIconSizePx = mIconSizeSteps.getIconSmallerThan(folderCellWidthPx);
            }

            // Recalculating padding and cell height
            folderChildDrawablePaddingPx = getNormalizedFolderChildDrawablePaddingPx(textHeight);
            int folderCellContentHeight = folderChildIconSizePx + folderChildDrawablePaddingPx
                    + textHeight;

            // Reduce the icon in height when it's taller than the expected cell height
            while (folderChildIconSizePx > mIconSizeSteps.minimumIconSize()
                    && folderCellContentHeight > folderCellHeightPx) {
                folderChildDrawablePaddingPx -= folderCellContentHeight - folderCellHeightPx;
                if (folderChildDrawablePaddingPx < 0) {
                    // get a smaller icon size
                    folderChildIconSizePx = mIconSizeSteps.getNextLowerIconSize(
                            folderChildIconSizePx);
                    folderChildDrawablePaddingPx =
                            getNormalizedFolderChildDrawablePaddingPx(textHeight);
                }
                // calculate new cellContentHeight
                folderCellContentHeight = folderChildIconSizePx + folderChildDrawablePaddingPx
                        + textHeight;
            }
        } else if (mIsScalableGrid) {
            if (inv.folderStyle == INVALID_RESOURCE_HANDLE) {
                folderCellWidthPx = roundPxValueFromFloat(getCellSize().x * scale);
                folderCellHeightPx = roundPxValueFromFloat(getCellSize().y * scale);
            } else {
                folderCellWidthPx = roundPxValueFromFloat(folderCellWidthPx * scale);
                folderCellHeightPx = roundPxValueFromFloat(folderCellHeightPx * scale);
            }

            folderContentPaddingTop = roundPxValueFromFloat(folderContentPaddingTop * scale);
            folderCellLayoutBorderSpacePx = new Point(
                    roundPxValueFromFloat(folderCellLayoutBorderSpacePx.x * scale),
                    roundPxValueFromFloat(folderCellLayoutBorderSpacePx.y * scale)
            );
            folderFooterHeightPx = roundPxValueFromFloat(folderFooterHeightPx * scale);

            folderContentPaddingLeftRight = folderCellLayoutBorderSpacePx.x;

            folderChildDrawablePaddingPx = getNormalizedFolderChildDrawablePaddingPx(textHeight);
        } else {
            int cellPaddingX = (int) (res.getDimensionPixelSize(R.dimen.folder_cell_x_padding)
                    * scale);
            int cellPaddingY = (int) (res.getDimensionPixelSize(R.dimen.folder_cell_y_padding)
                    * scale);

            folderCellWidthPx = folderChildIconSizePx + 2 * cellPaddingX;
            folderCellHeightPx = folderChildIconSizePx + 2 * cellPaddingY + textHeight;
            folderContentPaddingTop = roundPxValueFromFloat(folderContentPaddingTop * scale);
            folderContentPaddingLeftRight =
                    res.getDimensionPixelSize(R.dimen.folder_content_padding_left_right);
            folderFooterHeightPx =
                    roundPxValueFromFloat(
                            res.getDimensionPixelSize(R.dimen.folder_footer_height_default)
                                    * scale);

            folderChildDrawablePaddingPx = getNormalizedFolderChildDrawablePaddingPx(textHeight);
        }
    }

    public void updateInsets(Rect insets) {
        mInsets.set(insets);
    }

    /**
     * The current device insets. This is generally same as the insets being dispatched to
     * {@link Insettable} elements, but can differ if the element is using a different profile.
     */
    public Rect getInsets() {
        return mInsets;
    }

    public Point getCellSize() {
        return getCellSize(null);
    }

    public Point getCellSize(Point result) {
        if (result == null) {
            result = new Point();
        }

        int shortcutAndWidgetContainerWidth =
                getCellLayoutWidth() - (cellLayoutPaddingPx.left + cellLayoutPaddingPx.right);
        result.x = calculateCellWidth(shortcutAndWidgetContainerWidth, cellLayoutBorderSpacePx.x,
                inv.numColumns);
        int shortcutAndWidgetContainerHeight =
                getCellLayoutHeight() - (cellLayoutPaddingPx.top + cellLayoutPaddingPx.bottom);
        result.y = calculateCellHeight(shortcutAndWidgetContainerHeight, cellLayoutBorderSpacePx.y,
                inv.numRows);
        return result;
    }

    /**
     * Returns the left and right space on the cell, which is the cell width - icon size
     */
    public int getCellHorizontalSpace() {
        return getCellSize().x - iconSizePx;
    }

    /**
     * Gets the number of panels within the workspace.
     */
    public int getPanelCount() {
        return isTwoPanels ? 2 : 1;
    }

    /**
     * Gets the space in px from the bottom of last item in the vertical-bar hotseat to the
     * bottom of the screen.
     */
    private int getVerticalHotseatLastItemBottomOffset(Context context) {
        Rect hotseatBarPadding = getHotseatLayoutPadding(context);
        int cellHeight = calculateCellHeight(
                heightPx - hotseatBarPadding.top - hotseatBarPadding.bottom, hotseatBorderSpace,
                numShownHotseatIcons);
        int extraIconEndSpacing = (cellHeight - iconSizePx) / 2;
        return extraIconEndSpacing + hotseatBarPadding.bottom;
    }

    /**
     * Gets the scaled top of the workspace in px for the spring-loaded edit state.
     */
    public float getCellLayoutSpringLoadShrunkTop() {
        return mInsets.top + dropTargetBarTopMarginPx + dropTargetBarSizePx
                + dropTargetBarBottomMarginPx;
    }

    /**
     * Gets the scaled bottom of the workspace in px for the spring-loaded edit state.
     */
    public float getCellLayoutSpringLoadShrunkBottom(Context context) {
        int topOfHotseat = hotseatBarSizePx + springLoadedHotseatBarTopMarginPx;
        return heightPx - (isVerticalBarLayout()
                ? getVerticalHotseatLastItemBottomOffset(context) : topOfHotseat);
    }

    /**
     * Gets the scale of the workspace for the spring-loaded edit state.
     */
    public float getWorkspaceSpringLoadScale(Context context) {
        float scale =
                (getCellLayoutSpringLoadShrunkBottom(context) - getCellLayoutSpringLoadShrunkTop())
                        / getCellLayoutHeight();
        scale = Math.min(scale, 1f);

        // Reduce scale if next pages would not be visible after scaling the workspace.
        int workspaceWidth = availableWidthPx;
        float scaledWorkspaceWidth = workspaceWidth * scale;
        float maxAvailableWidth = workspaceWidth - (2 * workspaceSpringLoadedMinNextPageVisiblePx);
        if (scaledWorkspaceWidth > maxAvailableWidth) {
            scale *= maxAvailableWidth / scaledWorkspaceWidth;
        }
        return scale;
    }

    /**
     * Gets the width of a single Cell Layout, aka a single panel within a Workspace.
     *
     * <p>This is the width of a Workspace, less its horizontal padding. Note that two-panel
     * layouts have two Cell Layouts per workspace.
     */
    public int getCellLayoutWidth() {
        return (availableWidthPx - getTotalWorkspacePadding().x) / getPanelCount();
    }

    /**
     * Gets the height of a single Cell Layout, aka a single panel within a Workspace.
     *
     * <p>This is the height of a Workspace, less its vertical padding.
     */
    public int getCellLayoutHeight() {
        return availableHeightPx - getTotalWorkspacePadding().y;
    }

    public Point getTotalWorkspacePadding() {
        return new Point(workspacePadding.left + workspacePadding.right,
                workspacePadding.top + workspacePadding.bottom);
    }

    /**
     * Updates {@link #workspacePadding} as a result of any internal value change to reflect the
     * new workspace padding
     */
    private void updateWorkspacePadding() {
        Rect padding = workspacePadding;
        if (isVerticalBarLayout()) {
            if (mIsResponsiveGrid) {
                padding.top = mResponsiveHeightSpec.getStartPaddingPx();
                padding.bottom = mResponsiveHeightSpec.getEndPaddingPx();
                if (isSeascape()) {
                    padding.left = hotseatBarSizePx + mResponsiveWidthSpec.getEndPaddingPx();
                    padding.right = mResponsiveWidthSpec.getStartPaddingPx();
                } else {
                    padding.left = mResponsiveWidthSpec.getStartPaddingPx();
                    padding.right = hotseatBarSizePx + mResponsiveWidthSpec.getEndPaddingPx();
                }
            } else {
                padding.top = 0;
                padding.bottom = edgeMarginPx;
                if (isSeascape()) {
                    padding.left = hotseatBarSizePx;
                    padding.right = hotseatBarSidePaddingStartPx;
                } else {
                    padding.left = hotseatBarSidePaddingStartPx;
                    padding.right = hotseatBarSizePx;
                }
            }
        } else {
            // Pad the bottom of the workspace with hotseat bar
            // and leave a bit of space in case a widget go all the way down
            int paddingBottom = hotseatBarSizePx + workspaceBottomPadding - mInsets.bottom;
            if (!mIsResponsiveGrid) {
                paddingBottom +=
                        workspacePageIndicatorHeight - mWorkspacePageIndicatorOverlapWorkspace;
            }
            int paddingTop = workspaceTopPadding + (mIsScalableGrid ? 0 : edgeMarginPx);
            int paddingSide = desiredWorkspaceHorizontalMarginPx;

            padding.set(paddingSide, paddingTop, paddingSide, paddingBottom);
        }
        insetPadding(workspacePadding, cellLayoutPaddingPx);
    }

    private void insetPadding(Rect paddings, Rect insets) {
        insets.left = Math.min(insets.left, paddings.left);
        paddings.left -= insets.left;

        insets.top = Math.min(insets.top, paddings.top);
        paddings.top -= insets.top;

        insets.right = Math.min(insets.right, paddings.right);
        paddings.right -= insets.right;

        insets.bottom = Math.min(insets.bottom, paddings.bottom);
        paddings.bottom -= insets.bottom;
    }

    /**
     * Returns the padding for hotseat view
     */
    public Rect getHotseatLayoutPadding(Context context) {
        Rect hotseatBarPadding = new Rect();
        if (isVerticalBarLayout()) {
            // The hotseat icons will be placed in the middle of the hotseat cells.
            // Changing the hotseatCellHeightPx is not affecting hotseat icon positions
            // in vertical bar layout.
            // Workspace icons are moved up by a small factor. The variable diffOverlapFactor
            // is set to account for that difference.
            float diffOverlapFactor = mIsResponsiveGrid ? 0
                    : iconSizePx * (ICON_OVERLAP_FACTOR - 1) / 2;

            int paddingTop = Math.max((int) (mInsets.top + cellLayoutPaddingPx.top
                    - diffOverlapFactor), 0);
            int paddingBottom = Math.max((int) (mInsets.bottom + cellLayoutPaddingPx.bottom
                    + diffOverlapFactor), 0);

            if (isSeascape()) {
                hotseatBarPadding.set(mInsets.left + hotseatBarSidePaddingStartPx, paddingTop,
                        hotseatBarSidePaddingEndPx, paddingBottom);
            } else {
                hotseatBarPadding.set(hotseatBarSidePaddingEndPx, paddingTop,
                        mInsets.right + hotseatBarSidePaddingStartPx, paddingBottom);
            }
        } else if (isTaskbarPresent) {
            // Center the QSB vertically with hotseat
            int hotseatBarBottomPadding = getHotseatBarBottomPadding();
            int hotseatBarTopPadding =
                    hotseatBarSizePx - hotseatBarBottomPadding - hotseatCellHeightPx;

            int hotseatWidth = getHotseatRequiredWidth();
            int startSpacing;
            int endSpacing;
            // Hotseat aligns to the left with nav buttons
            if (hotseatBarEndOffset > 0) {
                startSpacing = inlineNavButtonsEndSpacingPx;
                endSpacing = availableWidthPx - hotseatWidth - startSpacing + hotseatBorderSpace;
            } else {
                startSpacing = (availableWidthPx - hotseatWidth) / 2;
                endSpacing = startSpacing;
            }
            startSpacing += getAdditionalQsbSpace();

            hotseatBarPadding.top = hotseatBarTopPadding;
            hotseatBarPadding.bottom = hotseatBarBottomPadding;
            boolean isRtl = Utilities.isRtl(context.getResources());
            if (isRtl) {
                hotseatBarPadding.left = endSpacing;
                hotseatBarPadding.right = startSpacing;
            } else {
                hotseatBarPadding.left = startSpacing;
                hotseatBarPadding.right = endSpacing;
            }

        } else if (mIsScalableGrid) {
            int sideSpacing = (availableWidthPx - hotseatQsbWidth) / 2;
            hotseatBarPadding.set(sideSpacing,
                    0,
                    sideSpacing,
                    getHotseatBarBottomPadding());
        } else {
            // We want the edges of the hotseat to line up with the edges of the workspace, but the
            // icons in the hotseat are a different size, and so don't line up perfectly. To account
            // for this, we pad the left and right of the hotseat with half of the difference of a
            // workspace cell vs a hotseat cell.
            float workspaceCellWidth = (float) widthPx / inv.numColumns;
            float hotseatCellWidth = (float) widthPx / numShownHotseatIcons;
            int hotseatAdjustment = hotseatBorderSpace / 2;
            hotseatBarPadding.set(
                    hotseatAdjustment + workspacePadding.left + cellLayoutPaddingPx.left
                            + mInsets.left,
                    0,
                    hotseatAdjustment + workspacePadding.right + cellLayoutPaddingPx.right
                            + mInsets.right,
                    getHotseatBarBottomPadding());
        }
        return hotseatBarPadding;
    }

    /** The margin between the edge of all apps and the edge of the first icon. */
    public int getAllAppsIconStartMargin() {
        int allAppsSpacing;
        if (isVerticalBarLayout()) {
            // On phones, the landscape layout uses a different setup.
            allAppsSpacing = workspacePadding.left + workspacePadding.right;
        } else {
            allAppsSpacing = allAppsLeftRightPadding * 2 + allAppsLeftRightMargin * 2;
        }

        int cellWidth = DeviceProfile.calculateCellWidth(
                availableWidthPx - allAppsSpacing,
                0 /* borderSpace */,
                numShownAllAppsColumns);
        int iconVisibleSize = Math.round(ICON_VISIBLE_AREA_FACTOR * allAppsIconSizePx);
        int iconAlignmentMargin = (cellWidth - iconVisibleSize) / 2;
        return allAppsLeftRightPadding + iconAlignmentMargin;
    }

    private int getAdditionalQsbSpace() {
        return isQsbInline ? hotseatQsbWidth + hotseatBorderSpace : 0;
    }

    /**
     * Calculate how much space the hotseat needs to be shown completely
     */
    private int getHotseatRequiredWidth() {
        int additionalQsbSpace = getAdditionalQsbSpace();
        return iconSizePx * numShownHotseatIcons
                + hotseatBorderSpace * (numShownHotseatIcons - (areNavButtonsInline ? 0 : 1))
                + additionalQsbSpace;
    }

    /**
     * Returns the number of pixels the QSB is translated from the bottom of the screen.
     */
    public int getQsbOffsetY() {
        if (isQsbInline) {
            return getHotseatBarBottomPadding() - ((hotseatQsbHeight - hotseatCellHeightPx) / 2);
        } else if (isTaskbarPresent) { // QSB on top
            return hotseatBarSizePx - hotseatQsbHeight + hotseatQsbShadowHeight;
        } else {
            return hotseatBarBottomSpacePx - hotseatQsbShadowHeight;
        }
    }

    /**
     * Returns the number of pixels the hotseat is translated from the bottom of the screen.
     */
    private int getHotseatBarBottomPadding() {
        if (isTaskbarPresent) { // QSB on top or inline
            return hotseatBarBottomSpacePx - (Math.abs(hotseatCellHeightPx - iconSizePx) / 2);
        } else {
            return hotseatBarSizePx - hotseatCellHeightPx;
        }
    }

    /**
     * Returns the number of pixels the taskbar is translated from the bottom of the screen.
     */
    public int getTaskbarOffsetY() {
        int taskbarIconBottomSpace = (taskbarHeight - iconSizePx) / 2;
        int launcherIconBottomSpace =
                Math.min((hotseatCellHeightPx - iconSizePx) / 2, gridVisualizationPaddingY);
        return getHotseatBarBottomPadding() + launcherIconBottomSpace - taskbarIconBottomSpace;
    }

    /**
     * Returns the number of pixels required below OverviewActions excluding insets.
     */
    public int getOverviewActionsClaimedSpaceBelow() {
        if (isTaskbarPresent) {
            return taskbarHeight + taskbarBottomMargin * 2;
        }
        return mInsets.bottom;
    }

    /** Gets the space that the overview actions will take, including bottom margin. */
    public int getOverviewActionsClaimedSpace() {
        int overviewActionsSpace = isTablet && FeatureFlags.ENABLE_GRID_ONLY_OVERVIEW.get()
                ? 0
                : (overviewActionsTopMarginPx + overviewActionsHeight);
        return overviewActionsSpace + getOverviewActionsClaimedSpaceBelow();
    }

    /**
     * Takes the View and return the scales of width and height depending on the DeviceProfile
     * specifications
     *
     * @param itemInfo The tag of the widget view
     * @return A PointF instance with the x set to be the scale of width, and y being the scale of
     * height
     */
    @NonNull
    public PointF getAppWidgetScale(@Nullable final ItemInfo itemInfo) {
        return mViewScaleProvider.getScaleFromItemInfo(itemInfo);
    }

    /**
     * @return the bounds for which the open folders should be contained within
     */
    public Rect getAbsoluteOpenFolderBounds() {
        if (isVerticalBarLayout()) {
            // Folders should only appear right of the drop target bar and left of the hotseat
            return new Rect(mInsets.left + dropTargetBarSizePx + edgeMarginPx,
                    mInsets.top,
                    mInsets.left + availableWidthPx - hotseatBarSizePx - edgeMarginPx,
                    mInsets.top + availableHeightPx);
        } else {
            // Folders should only appear below the drop target bar and above the hotseat
            int hotseatTop = isTaskbarPresent ? taskbarHeight : hotseatBarSizePx;
            return new Rect(mInsets.left + edgeMarginPx,
                    mInsets.top + dropTargetBarSizePx + edgeMarginPx,
                    mInsets.left + availableWidthPx - edgeMarginPx,
                    mInsets.top + availableHeightPx - hotseatTop
                            - workspacePageIndicatorHeight - edgeMarginPx);
        }
    }

    public static int calculateCellWidth(int width, int borderSpacing, int countX) {
        return (width - ((countX - 1) * borderSpacing)) / countX;
    }

    public static int calculateCellHeight(int height, int borderSpacing, int countY) {
        return (height - ((countY - 1) * borderSpacing)) / countY;
    }

    /**
     * When {@code true}, the device is in landscape mode and the hotseat is on the right column.
     * When {@code false}, either device is in portrait mode or the device is in landscape mode and
     * the hotseat is on the bottom row.
     */
    public boolean isVerticalBarLayout() {
        return isLandscape && transposeLayoutWithOrientation;
    }

    /**
     * Updates orientation information and returns true if it has changed from the previous value.
     */
    public boolean updateIsSeascape(Context context) {
        if (isVerticalBarLayout()) {
            boolean isSeascape = DisplayController.INSTANCE.get(context)
                    .getInfo().rotation == Surface.ROTATION_270;
            if (mIsSeascape != isSeascape) {
                mIsSeascape = isSeascape;
                // Hotseat changing sides requires updating workspace left/right paddings
                updateWorkspacePadding();
                return true;
            }
        }
        return false;
    }

    public boolean isSeascape() {
        return isVerticalBarLayout() && mIsSeascape;
    }

    public boolean shouldFadeAdjacentWorkspaceScreens() {
        return isVerticalBarLayout();
    }

    public int getCellContentHeight(@ContainerType int containerType) {
        switch (containerType) {
            case CellLayout.WORKSPACE:
                return cellHeightPx;
            case CellLayout.FOLDER:
                return folderCellHeightPx;
            case CellLayout.HOTSEAT:
                // The hotseat is the only container where the cell height is going to be
                // different from the content within that cell.
                return iconSizePx;
            default:
                // ??
                return 0;
        }
    }

    private String pxToDpStr(String name, float value) {
        return "\t" + name + ": " + value + "px (" + dpiFromPx(value, mMetrics.densityDpi) + "dp)";
    }

    private String dpPointFToString(String name, PointF value) {
        return String.format(Locale.ENGLISH, "\t%s: PointF(%.1f, %.1f)dp", name, value.x, value.y);
    }

    /** Dumps various DeviceProfile variables to the specified writer. */
    public void dump(Context context, String prefix, PrintWriter writer) {
        writer.println(prefix + "DeviceProfile:");
        writer.println(prefix + "\t1 dp = " + mMetrics.density + " px");

        writer.println(prefix + "\tisTablet:" + isTablet);
        writer.println(prefix + "\tisPhone:" + isPhone);
        writer.println(prefix + "\ttransposeLayoutWithOrientation:"
                + transposeLayoutWithOrientation);
        writer.println(prefix + "\tisGestureMode:" + isGestureMode);

        writer.println(prefix + "\tisLandscape:" + isLandscape);
        writer.println(prefix + "\tisMultiWindowMode:" + isMultiWindowMode);
        writer.println(prefix + "\tisTwoPanels:" + isTwoPanels);

        writer.println(prefix + pxToDpStr("windowX", windowX));
        writer.println(prefix + pxToDpStr("windowY", windowY));
        writer.println(prefix + pxToDpStr("widthPx", widthPx));
        writer.println(prefix + pxToDpStr("heightPx", heightPx));
        writer.println(prefix + pxToDpStr("availableWidthPx", availableWidthPx));
        writer.println(prefix + pxToDpStr("availableHeightPx", availableHeightPx));
        writer.println(prefix + pxToDpStr("mInsets.left", mInsets.left));
        writer.println(prefix + pxToDpStr("mInsets.top", mInsets.top));
        writer.println(prefix + pxToDpStr("mInsets.right", mInsets.right));
        writer.println(prefix + pxToDpStr("mInsets.bottom", mInsets.bottom));

        writer.println(prefix + "\taspectRatio:" + aspectRatio);

        writer.println(prefix + "\tisResponsiveGrid:" + mIsResponsiveGrid);
        writer.println(prefix + "\tisScalableGrid:" + mIsScalableGrid);

        writer.println(prefix + "\tinv.numRows: " + inv.numRows);
        writer.println(prefix + "\tinv.numColumns: " + inv.numColumns);
        writer.println(prefix + "\tinv.numSearchContainerColumns: "
                + inv.numSearchContainerColumns);

        writer.println(prefix + dpPointFToString("minCellSize", inv.minCellSize[mTypeIndex]));

        writer.println(prefix + pxToDpStr("cellWidthPx", cellWidthPx));
        writer.println(prefix + pxToDpStr("cellHeightPx", cellHeightPx));

        writer.println(prefix + pxToDpStr("getCellSize().x", getCellSize().x));
        writer.println(prefix + pxToDpStr("getCellSize().y", getCellSize().y));

        writer.println(prefix + pxToDpStr("cellLayoutBorderSpacePx Horizontal",
                cellLayoutBorderSpacePx.x));
        writer.println(prefix + pxToDpStr("cellLayoutBorderSpacePx Vertical",
                cellLayoutBorderSpacePx.y));
        writer.println(
                prefix + pxToDpStr("cellLayoutPaddingPx.left", cellLayoutPaddingPx.left));
        writer.println(
                prefix + pxToDpStr("cellLayoutPaddingPx.top", cellLayoutPaddingPx.top));
        writer.println(
                prefix + pxToDpStr("cellLayoutPaddingPx.right", cellLayoutPaddingPx.right));
        writer.println(
                prefix + pxToDpStr("cellLayoutPaddingPx.bottom", cellLayoutPaddingPx.bottom));

        writer.println(prefix + pxToDpStr("iconSizePx", iconSizePx));
        writer.println(prefix + pxToDpStr("iconTextSizePx", iconTextSizePx));
        writer.println(prefix + pxToDpStr("iconDrawablePaddingPx", iconDrawablePaddingPx));

        writer.println(prefix + "\tinv.numFolderRows: " + inv.numFolderRows);
        writer.println(prefix + "\tinv.numFolderColumns: " + inv.numFolderColumns);
        writer.println(prefix + pxToDpStr("folderCellWidthPx", folderCellWidthPx));
        writer.println(prefix + pxToDpStr("folderCellHeightPx", folderCellHeightPx));
        writer.println(prefix + pxToDpStr("folderChildIconSizePx", folderChildIconSizePx));
        writer.println(prefix + pxToDpStr("folderChildTextSizePx", folderChildTextSizePx));
        writer.println(prefix + pxToDpStr("folderChildDrawablePaddingPx",
                folderChildDrawablePaddingPx));
        writer.println(prefix + pxToDpStr("folderCellLayoutBorderSpacePx.x",
                folderCellLayoutBorderSpacePx.x));
        writer.println(prefix + pxToDpStr("folderCellLayoutBorderSpacePx.y",
                folderCellLayoutBorderSpacePx.y));
        writer.println(prefix + pxToDpStr("folderContentPaddingLeftRight",
                folderContentPaddingLeftRight));
        writer.println(prefix + pxToDpStr("folderTopPadding", folderContentPaddingTop));
        writer.println(prefix + pxToDpStr("folderFooterHeight", folderFooterHeightPx));

        writer.println(prefix + pxToDpStr("bottomSheetTopPadding", bottomSheetTopPadding));
        writer.println(prefix + "\tbottomSheetOpenDuration: " + bottomSheetOpenDuration);
        writer.println(prefix + "\tbottomSheetCloseDuration: " + bottomSheetCloseDuration);
        writer.println(prefix + "\tbottomSheetWorkspaceScale: " + bottomSheetWorkspaceScale);
        writer.println(prefix + "\tbottomSheetDepth: " + bottomSheetDepth);

        writer.println(prefix + pxToDpStr("allAppsShiftRange", allAppsShiftRange));
        writer.println(prefix + pxToDpStr("allAppsTopPadding", allAppsTopPadding));
        writer.println(prefix + "\tallAppsOpenDuration: " + allAppsOpenDuration);
        writer.println(prefix + "\tallAppsCloseDuration: " + allAppsCloseDuration);
        writer.println(prefix + pxToDpStr("allAppsIconSizePx", allAppsIconSizePx));
        writer.println(prefix + pxToDpStr("allAppsIconTextSizePx", allAppsIconTextSizePx));
        writer.println(prefix + pxToDpStr("allAppsIconDrawablePaddingPx",
                allAppsIconDrawablePaddingPx));
        writer.println(prefix + pxToDpStr("allAppsCellHeightPx", allAppsCellHeightPx));
        writer.println(prefix + pxToDpStr("allAppsCellWidthPx", allAppsCellWidthPx));
        writer.println(prefix + pxToDpStr("allAppsBorderSpacePxX", allAppsBorderSpacePx.x));
        writer.println(prefix + pxToDpStr("allAppsBorderSpacePxY", allAppsBorderSpacePx.y));
        writer.println(prefix + "\tnumShownAllAppsColumns: " + numShownAllAppsColumns);
        writer.println(prefix + pxToDpStr("allAppsLeftRightPadding", allAppsLeftRightPadding));
        writer.println(prefix + pxToDpStr("allAppsLeftRightMargin", allAppsLeftRightMargin));

        writer.println(prefix + pxToDpStr("hotseatBarSizePx", hotseatBarSizePx));
        writer.println(prefix + "\tinv.hotseatColumnSpan: " + inv.hotseatColumnSpan[mTypeIndex]);
        writer.println(prefix + pxToDpStr("hotseatCellHeightPx", hotseatCellHeightPx));
        writer.println(prefix + pxToDpStr("hotseatBarBottomSpacePx", hotseatBarBottomSpacePx));
        writer.println(prefix + pxToDpStr("hotseatBarSidePaddingStartPx",
                hotseatBarSidePaddingStartPx));
        writer.println(prefix + pxToDpStr("hotseatBarSidePaddingEndPx",
                hotseatBarSidePaddingEndPx));
        writer.println(prefix + pxToDpStr("hotseatBarEndOffset", hotseatBarEndOffset));
        writer.println(prefix + pxToDpStr("hotseatQsbSpace", hotseatQsbSpace));
        writer.println(prefix + pxToDpStr("hotseatQsbHeight", hotseatQsbHeight));
        writer.println(prefix + pxToDpStr("springLoadedHotseatBarTopMarginPx",
                springLoadedHotseatBarTopMarginPx));
        Rect hotseatLayoutPadding = getHotseatLayoutPadding(context);
        writer.println(prefix + pxToDpStr("getHotseatLayoutPadding(context).top",
                hotseatLayoutPadding.top));
        writer.println(prefix + pxToDpStr("getHotseatLayoutPadding(context).bottom",
                hotseatLayoutPadding.bottom));
        writer.println(prefix + pxToDpStr("getHotseatLayoutPadding(context).left",
                hotseatLayoutPadding.left));
        writer.println(prefix + pxToDpStr("getHotseatLayoutPadding(context).right",
                hotseatLayoutPadding.right));
        writer.println(prefix + "\tnumShownHotseatIcons: " + numShownHotseatIcons);
        writer.println(prefix + pxToDpStr("hotseatBorderSpace", hotseatBorderSpace));
        writer.println(prefix + "\tisQsbInline: " + isQsbInline);
        writer.println(prefix + pxToDpStr("hotseatQsbWidth", hotseatQsbWidth));

        writer.println(prefix + "\tisTaskbarPresent:" + isTaskbarPresent);
        writer.println(prefix + "\tisTaskbarPresentInApps:" + isTaskbarPresentInApps);
        writer.println(prefix + pxToDpStr("taskbarHeight", taskbarHeight));
        writer.println(prefix + pxToDpStr("stashedTaskbarHeight", stashedTaskbarHeight));
        writer.println(prefix + pxToDpStr("taskbarBottomMargin", taskbarBottomMargin));
        writer.println(prefix + pxToDpStr("taskbarIconSize", taskbarIconSize));

        writer.println(prefix + pxToDpStr("desiredWorkspaceHorizontalMarginPx",
                desiredWorkspaceHorizontalMarginPx));
        writer.println(prefix + pxToDpStr("workspacePadding.left", workspacePadding.left));
        writer.println(prefix + pxToDpStr("workspacePadding.top", workspacePadding.top));
        writer.println(prefix + pxToDpStr("workspacePadding.right", workspacePadding.right));
        writer.println(prefix + pxToDpStr("workspacePadding.bottom", workspacePadding.bottom));

        writer.println(prefix + pxToDpStr("iconScale", iconScale));
        writer.println(prefix + pxToDpStr("cellScaleToFit ", cellScaleToFit));
        writer.println(prefix + pxToDpStr("extraSpace", extraSpace));
        writer.println(prefix + pxToDpStr("unscaled extraSpace", extraSpace / iconScale));

        writer.println(prefix + pxToDpStr("maxEmptySpace", maxEmptySpace));
        writer.println(prefix + pxToDpStr("workspaceTopPadding", workspaceTopPadding));
        writer.println(prefix + pxToDpStr("workspaceBottomPadding", workspaceBottomPadding));

        writer.println(prefix + pxToDpStr("overviewTaskMarginPx", overviewTaskMarginPx));
        writer.println(prefix + pxToDpStr("overviewTaskIconSizePx", overviewTaskIconSizePx));
        writer.println(prefix + pxToDpStr("overviewTaskIconDrawableSizePx",
                overviewTaskIconDrawableSizePx));
        writer.println(prefix + pxToDpStr("overviewTaskIconDrawableSizeGridPx",
                overviewTaskIconDrawableSizeGridPx));
        writer.println(prefix + pxToDpStr("overviewTaskThumbnailTopMarginPx",
                overviewTaskThumbnailTopMarginPx));
        writer.println(prefix + pxToDpStr("overviewActionsTopMarginPx",
                overviewActionsTopMarginPx));
        writer.println(prefix + pxToDpStr("overviewActionsHeight",
                overviewActionsHeight));
        writer.println(prefix + pxToDpStr("overviewActionsClaimedSpaceBelow",
                getOverviewActionsClaimedSpaceBelow()));
        writer.println(prefix + pxToDpStr("overviewActionsButtonSpacing",
                overviewActionsButtonSpacing));
        writer.println(prefix + pxToDpStr("overviewPageSpacing", overviewPageSpacing));
        writer.println(prefix + pxToDpStr("overviewRowSpacing", overviewRowSpacing));
        writer.println(prefix + pxToDpStr("overviewGridSideMargin", overviewGridSideMargin));

        writer.println(prefix + pxToDpStr("dropTargetBarTopMarginPx", dropTargetBarTopMarginPx));
        writer.println(prefix + pxToDpStr("dropTargetBarSizePx", dropTargetBarSizePx));
        writer.println(
                prefix + pxToDpStr("dropTargetBarBottomMarginPx", dropTargetBarBottomMarginPx));

        writer.println(prefix + pxToDpStr("getCellLayoutSpringLoadShrunkTop()",
                getCellLayoutSpringLoadShrunkTop()));
        writer.println(prefix + pxToDpStr("getCellLayoutSpringLoadShrunkBottom()",
                getCellLayoutSpringLoadShrunkBottom(context)));
        writer.println(prefix + pxToDpStr("workspaceSpringLoadedMinNextPageVisiblePx",
                workspaceSpringLoadedMinNextPageVisiblePx));
        writer.println(prefix + pxToDpStr("getWorkspaceSpringLoadScale()",
                getWorkspaceSpringLoadScale(context)));
        writer.println(prefix + pxToDpStr("getCellLayoutHeight()", getCellLayoutHeight()));
        writer.println(prefix + pxToDpStr("getCellLayoutWidth()", getCellLayoutWidth()));
        if (mIsResponsiveGrid) {
            writer.println(prefix + "\tmResponsiveHeightSpec:" + mResponsiveHeightSpec.toString());
            writer.println(prefix + "\tmResponsiveWidthSpec:" + mResponsiveWidthSpec.toString());
            writer.println(prefix + "\tmAllAppsResponsiveHeightSpec:"
                    + mAllAppsResponsiveHeightSpec.toString());
            writer.println(prefix + "\tmAllAppsResponsiveWidthSpec:"
                    + mAllAppsResponsiveWidthSpec.toString());
            writer.println(prefix + "\tmResponsiveFolderHeightSpec:" + mResponsiveFolderHeightSpec);
            writer.println(prefix + "\tmResponsiveFolderWidthSpec:" + mResponsiveFolderWidthSpec);
            writer.println(prefix + "\tmResponsiveHotseatSpec:" + mResponsiveHotseatSpec);
        }
    }

    /** Returns a reduced representation of this DeviceProfile. */
    public String toSmallString() {
        return "isTablet:" + isTablet + ", "
                + "isMultiDisplay:" + isMultiDisplay + ", "
                + "widthPx:" + widthPx + ", "
                + "heightPx:" + heightPx + ", "
                + "insets:" + mInsets + ", "
                + "rotationHint:" + rotationHint;
    }

    private static Context getContext(Context c, Info info, int orientation, WindowBounds bounds) {
        Configuration config = new Configuration(c.getResources().getConfiguration());
        config.orientation = orientation;
        config.densityDpi = info.getDensityDpi();
        config.smallestScreenWidthDp = (int) info.smallestSizeDp(bounds);
        return c.createConfigurationContext(config);
    }

    /**
     * Callback when a component changes the DeviceProfile associated with it, as a result of
     * configuration change
     */
    public interface OnDeviceProfileChangeListener {

        /**
         * Called when the device profile is reassigned. Note that for layout and measurements, it
         * is sufficient to listen for inset changes. Use this callback when you need to perform
         * a one time operation.
         */
        void onDeviceProfileChanged(DeviceProfile dp);
    }

    /**
     * Handler that deals with ItemInfo of the views for the DeviceProfile
     */
    @FunctionalInterface
    public interface ViewScaleProvider {
        /**
         * Get the scales from the view
         *
         * @param itemInfo The tag of the widget view
         * @return PointF instance containing the scale information, or null if using the default
         * app widget scale of this device profile.
         */
        @NonNull
        PointF getScaleFromItemInfo(@Nullable ItemInfo itemInfo);
    }

    public static class Builder {
        private Context mContext;
        private InvariantDeviceProfile mInv;
        private Info mInfo;

        private WindowBounds mWindowBounds;
        private boolean mIsMultiDisplay;

        private boolean mIsMultiWindowMode = false;
        private Boolean mTransposeLayoutWithOrientation;
        private Boolean mIsGestureMode;
        private ViewScaleProvider mViewScaleProvider = null;

        private SparseArray<DotRenderer> mDotRendererCache;

        private Consumer<DeviceProfile> mOverrideProvider;

        public Builder(Context context, InvariantDeviceProfile inv, Info info) {
            mContext = context;
            mInv = inv;
            mInfo = info;
        }

        public Builder setMultiWindowMode(boolean isMultiWindowMode) {
            mIsMultiWindowMode = isMultiWindowMode;
            return this;
        }

        public Builder setIsMultiDisplay(boolean isMultiDisplay) {
            mIsMultiDisplay = isMultiDisplay;
            return this;
        }

        public Builder setDotRendererCache(SparseArray<DotRenderer> dotRendererCache) {
            mDotRendererCache = dotRendererCache;
            return this;
        }

        public Builder setWindowBounds(WindowBounds bounds) {
            mWindowBounds = bounds;
            return this;
        }

        public Builder setTransposeLayoutWithOrientation(boolean transposeLayoutWithOrientation) {
            mTransposeLayoutWithOrientation = transposeLayoutWithOrientation;
            return this;
        }

        public Builder setGestureMode(boolean isGestureMode) {
            mIsGestureMode = isGestureMode;
            return this;
        }

        public Builder withDimensionsOverride(Consumer<DeviceProfile> overrideProvider) {
            mOverrideProvider = overrideProvider;
            return this;
        }

        /**
         * Set the viewScaleProvider for the builder
         *
         * @param viewScaleProvider The viewScaleProvider to be set for the
         *                          DeviceProfile
         * @return This builder
         */
        @NonNull
        public Builder setViewScaleProvider(@Nullable ViewScaleProvider viewScaleProvider) {
            mViewScaleProvider = viewScaleProvider;
            return this;
        }

        public DeviceProfile build() {
            if (mWindowBounds == null) {
                throw new IllegalArgumentException("Window bounds not set");
            }
            if (mTransposeLayoutWithOrientation == null) {
                mTransposeLayoutWithOrientation = !mInfo.isTablet(mWindowBounds);
            }
            if (mIsGestureMode == null) {
                mIsGestureMode = mInfo.navigationMode.hasGestures;
            }
            if (mDotRendererCache == null) {
                mDotRendererCache = new SparseArray<>();
            }
            if (mViewScaleProvider == null) {
                mViewScaleProvider = DEFAULT_PROVIDER;
            }
            if (mOverrideProvider == null) {
                mOverrideProvider = DEFAULT_DIMENSION_PROVIDER;
            }
            return new DeviceProfile(mContext, mInv, mInfo, mWindowBounds, mDotRendererCache,
                    mIsMultiWindowMode, mTransposeLayoutWithOrientation, mIsMultiDisplay,
                    mIsGestureMode, mViewScaleProvider, mOverrideProvider);
        }
    }

}
