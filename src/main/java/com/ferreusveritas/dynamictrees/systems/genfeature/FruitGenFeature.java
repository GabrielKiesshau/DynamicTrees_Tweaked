package com.ferreusveritas.dynamictrees.systems.genfeature;

import com.ferreusveritas.dynamictrees.api.TreeHelper;
import com.ferreusveritas.dynamictrees.api.configuration.ConfigurationProperty;
import com.ferreusveritas.dynamictrees.api.network.MapSignal;
import com.ferreusveritas.dynamictrees.block.branch.BranchBlock;
import com.ferreusveritas.dynamictrees.compat.season.SeasonHelper;
import com.ferreusveritas.dynamictrees.systems.fruit.Fruit;
import com.ferreusveritas.dynamictrees.systems.genfeature.context.PostGenerationContext;
import com.ferreusveritas.dynamictrees.systems.genfeature.context.PostGrowContext;
import com.ferreusveritas.dynamictrees.systems.nodemapper.FindEndsNode;
import com.ferreusveritas.dynamictrees.tree.species.Species;
import com.ferreusveritas.dynamictrees.util.CoordUtils;
import com.ferreusveritas.dynamictrees.util.SafeChunkBounds;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.state.BlockState;

import java.util.List;

public class FruitGenFeature extends GenFeature {

    public static final ConfigurationProperty<Fruit> FRUIT = ConfigurationProperty.property("fruit", Fruit.class);

    public FruitGenFeature(ResourceLocation registryName) {
        super(registryName);
    }

    @Override
    protected void registerProperties() {
        this.register(FRUIT, VERTICAL_SPREAD, QUANTITY, RAY_DISTANCE, FRUITING_RADIUS, PLACE_CHANCE);
    }

    @Override
    public GenFeatureConfiguration createDefaultConfiguration() {
        return super.createDefaultConfiguration()
                .with(FRUIT, Fruit.NULL)
                .with(VERTICAL_SPREAD, 30f)
                .with(QUANTITY, 4)
                .with(FRUITING_RADIUS, 8)
                .with(PLACE_CHANCE, 1f);
    }

    @Override
    public boolean shouldApply(Species species, GenFeatureConfiguration configuration) {
        return species.hasFruit(configuration.get(FRUIT));
    }

    @Override
    protected boolean postGenerate(GenFeatureConfiguration configuration, PostGenerationContext context) {
        final var branchPosList = context.endPoints();

        if (branchPosList.isEmpty()) {
            return false;
        }

        final var treePos = context.pos().above();

        int attempts = configuration.get(QUANTITY);
        attempts *= context.fruitProductionFactor() * 6;

        for (final var branchPos : branchPosList) {
            this.placeDuringWorldGen(configuration, context.species(), context.level(), treePos, branchPos, context.bounds(), context.seasonValue(), attempts);
        }

        return false;
    }

    @Override
    protected boolean postGrow(GenFeatureConfiguration configuration, PostGrowContext context) {
        final LevelAccessor level = context.level();
        final BlockState blockState = level.getBlockState(context.treePos());
        final BranchBlock branch = TreeHelper.getBranch(blockState);

        if (branch != null && branch.getRadius(blockState) >= configuration.get(FRUITING_RADIUS) && context.natural()) {
            final BlockPos rootPos = context.pos();

            var currentSeason = SeasonHelper.getSeasonValue(context.levelContext(), BlockPos.ZERO);

            final Fruit fruit = configuration.get(FRUIT);

            if (!isFruitInSeason(fruit, currentSeason)) {
                return false;
            }

            float fruitingFactor = fruit.seasonalFruitProductionFactor(context.levelContext(), rootPos);

            if (fruitingFactor > fruit.getMinProductionFactor() && fruitingFactor > level.getRandom().nextFloat()) {
                final FindEndsNode endFinder = new FindEndsNode();
                TreeHelper.startAnalysisFromRoot(level, rootPos, new MapSignal(endFinder));
                final List<BlockPos> branchPosList = endFinder.getEnds();

                if (branchPosList.isEmpty()) {
                    return false;
                }

                int attempts = configuration.get(QUANTITY);

                for (var branchPos : branchPosList) {
                    this.place(configuration, context.species(), level, rootPos.above(), branchPos, currentSeason, attempts);
                }
            }
        }

        return true;
    }

    protected void place(GenFeatureConfiguration configuration, Species species, LevelAccessor level, BlockPos treePos, BlockPos branchPos, Float currentSeason, int attempts) {
        final BlockPos fruitPos = CoordUtils.getRayTraceFruitPos(level, species, treePos, branchPos, SafeChunkBounds.ANY, attempts);
        if (shouldPlace(configuration, level, fruitPos, currentSeason)) {
            configuration.get(FRUIT).place(level, fruitPos, currentSeason);
        }
    }

    protected boolean shouldPlace(GenFeatureConfiguration configuration, LevelAccessor level, BlockPos pos, Float currentSeason) {
        if (pos == BlockPos.ZERO) {
            return false;
        }

        final Fruit fruit = configuration.get(FRUIT);

        if (!isFruitInSeason(fruit, currentSeason)) {
            return false;
        }

        // Random chance based on PLACE_CHANCE
        float chance = configuration.get(PLACE_CHANCE);

        // Add randomness based on tree growth state
        final BlockState blockState = level.getBlockState(pos.below()); // Check the block below (branch)
        if (TreeHelper.isBranch(blockState)) {
            final BranchBlock branch = (BranchBlock) blockState.getBlock();
            int radius = branch.getRadius(blockState);
            chance *= (radius * 0.75f); // Scale chance based on branch radius
        }

        return level.getRandom().nextFloat() <= chance;
    }

    protected void placeDuringWorldGen(GenFeatureConfiguration configuration, Species species, LevelAccessor level, BlockPos treePos, BlockPos branchPos, SafeChunkBounds bounds, Float currentSeason, int attempts) {
        final BlockPos fruitPos = CoordUtils.getRayTraceFruitPos(level, species, treePos, branchPos, bounds, attempts);
        if (shouldPlaceDuringWorldGen(configuration, level, fruitPos)) {
            species.getGenFeatures();
            configuration.get(FRUIT).placeDuringWorldGen(level, fruitPos, currentSeason);
        }
    }

    protected boolean shouldPlaceDuringWorldGen(GenFeatureConfiguration configuration, LevelAccessor level, BlockPos pos) {
        return pos != BlockPos.ZERO && level.getRandom().nextFloat() <= configuration.get(PLACE_CHANCE);
    }

    private boolean isFruitInSeason(Fruit fruit, Float currentSeason) {
        var fruitStart = fruit.getSeasonOffset();

        if (fruitStart == null) {
            return true;
        }

        currentSeason = currentSeason % 4.0f;
        fruitStart += 1;
        fruitStart = fruitStart % 4.0f;
        var fruitEnd = (fruitStart + 1.0f) % 4.0f;

        if (fruitStart > fruitEnd) {
            return fruitStart <= currentSeason || currentSeason <= fruitEnd;
        }

        return fruitStart <= currentSeason && currentSeason <= fruitEnd;
    }
}
