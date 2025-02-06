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
import net.minecraft.server.level.ServerLevel;

public class FruitGenFeature extends GenFeature {

    public static final ConfigurationProperty<Fruit> FRUIT = ConfigurationProperty.property("fruit", Fruit.class);

    public FruitGenFeature(ResourceLocation registryName) {
        super(registryName);
    }

    @Override
    protected void registerProperties() {
        this.register(FRUIT, VERTICAL_SPREAD, QUANTITY, RAY_DISTANCE, FRUITING_RADIUS, PLACE_CHANCE, CLEAR_WEATHER_MODIFIER, RAIN_MODIFIER, STORM_MODIFIER);
    }

    @Override
    public GenFeatureConfiguration createDefaultConfiguration() {
        return super.createDefaultConfiguration()
                .with(FRUIT, Fruit.NULL)
                .with(VERTICAL_SPREAD, 30f)
                .with(QUANTITY, 4)
                .with(FRUITING_RADIUS, 8)
                .with(PLACE_CHANCE, 1f)
                .with(CLEAR_WEATHER_MODIFIER, 1.0f)
                .with(RAIN_MODIFIER, 1.2f)
                .with(STORM_MODIFIER, 0.8f);
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
        final Fruit fruit = configuration.get(FRUIT);

        if (branch != null && branch.getRadius(blockState) >= configuration.get(FRUITING_RADIUS) && context.natural()) {
            final BlockPos rootPos = context.pos();
            float fruitingFactor = fruit.seasonalFruitProductionFactor(context.levelContext(), rootPos);

            // Apply weather modifiers
            float weatherModifier = configuration.get(CLEAR_WEATHER_MODIFIER);
            if (level instanceof ServerLevel serverLevel) {
                if (serverLevel.isRaining()) {
                    weatherModifier *= configuration.get(RAIN_MODIFIER);
                }
                if (serverLevel.isThundering()) {
                    weatherModifier *= configuration.get(STORM_MODIFIER);
                }
            }
            fruitingFactor *= weatherModifier;

            if (fruitingFactor > fruit.getMinProductionFactor() && fruitingFactor > level.getRandom().nextFloat()) {
                final FindEndsNode endFinder = new FindEndsNode();
                TreeHelper.startAnalysisFromRoot(level, rootPos, new MapSignal(endFinder));
                final List<BlockPos> branchPosList = endFinder.getEnds();

                if (branchPosList.isEmpty()) {
                    return false;
                }

                int attempts = configuration.get(QUANTITY);

                for (var branchPos : branchPosList) {
                    this.place(configuration, context.species(), level, rootPos.above(), branchPos, SeasonHelper.getSeasonValue(context.levelContext(), rootPos), attempts);
                }
            }
        }

        return true;
    }

    protected void place(GenFeatureConfiguration configuration, Species species, LevelAccessor level, BlockPos treePos, BlockPos branchPos, Float seasonValue, int attempts) {
        final BlockPos fruitPos = CoordUtils.getRayTraceFruitPos(level, species, treePos, branchPos, SafeChunkBounds.ANY, attempts);
        if (shouldPlace(configuration, level, fruitPos)) {
            configuration.get(FRUIT).place(level, fruitPos, seasonValue);
        }
    }

    protected boolean shouldPlace(GenFeatureConfiguration configuration, LevelAccessor level, BlockPos pos) {
        if (pos == BlockPos.ZERO) {
            return false;
        }

        // Random chance based on PLACE_CHANCE
        float chance = configuration.get(PLACE_CHANCE);

        // Add randomness based on tree growth state
        BlockState blockState = level.getBlockState(pos.below()); // Check the block below (branch)
        if (TreeHelper.isBranch(blockState)) {
            BranchBlock branch = (BranchBlock) blockState.getBlock();
            int radius = branch.getRadius(blockState);
            chance *= (radius * 0.75f); // Scale chance based on branch radius
        }

        // Add randomness based on environmental factors
        if (level instanceof ServerLevel serverLevel) {
            if (serverLevel.isRaining()) {
                chance *= 1.2f; // Increase chance during rain
            }
            if (serverLevel.isThundering()) {
                chance *= 0.8f; // Decrease chance during storms
            }
        }

        return level.getRandom().nextFloat() <= chance;
    }

    protected void placeDuringWorldGen(GenFeatureConfiguration configuration, Species species, LevelAccessor level, BlockPos treePos, BlockPos branchPos, SafeChunkBounds bounds, Float seasonValue, int attempts) {
        final BlockPos fruitPos = CoordUtils.getRayTraceFruitPos(level, species, treePos, branchPos, bounds, attempts);
        if (shouldPlaceDuringWorldGen(configuration, level, fruitPos)) {
            configuration.get(FRUIT).placeDuringWorldGen(level, fruitPos, seasonValue);
        }
    }

    protected boolean shouldPlaceDuringWorldGen(GenFeatureConfiguration configuration, LevelAccessor level, BlockPos pos) {
        return pos != BlockPos.ZERO && level.getRandom().nextFloat() <= configuration.get(PLACE_CHANCE);
    }
}
