/*
 *  Licensed to GraphHopper GmbH under one or more contributor
 *  license agreements. See the NOTICE file distributed with this work for 
 *  additional information regarding copyright ownership.
 * 
 *  GraphHopper GmbH licenses this file to you under the Apache License, 
 *  Version 2.0 (the "License"); you may not use this file except in 
 *  compliance with the License. You may obtain a copy of the License at
 * 
 *       http://www.apache.org/licenses/LICENSE-2.0
 * 
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */
package heigit.ors.routing.graphhopper.extensions.flagencoders.deprecated;

import com.graphhopper.reader.ReaderWay;
import com.graphhopper.routing.weighting.PriorityWeighting;
import com.graphhopper.util.PMap;
import heigit.ors.routing.graphhopper.extensions.flagencoders.FlagEncoderNames;
import heigit.ors.routing.graphhopper.extensions.flagencoders.deprecated.exghoverwrite.ExGhORSFootFlagEncoder;
import org.apache.log4j.Logger;

import java.util.TreeMap;

import static com.graphhopper.routing.util.PriorityCode.*;

/**
 * Defines bit layout for hiking
 *
 * @author Peter Karich
 */
public class HikingFlagEncoder extends ExGhORSFootFlagEncoder
{
    /**
     * Should be only instantiated via EncodingManager
     */
    public HikingFlagEncoder()
    {
        this(4, 1);
    }

    public HikingFlagEncoder( PMap properties )
    {
        this((int) properties.getLong("speed_bits", 4),
                properties.getDouble("speed_factor", 1));
        this.properties = properties;

        // MARQ24 why the heck we ste "block_fords" as default for HIKING?!
        // for regular foot that would had been fine - but for hiking?!
        this.setBlockFords(properties.getBool("block_fords", true));
    }

    public HikingFlagEncoder( String propertiesStr )
    {
        this(new PMap(propertiesStr));
    }

    public HikingFlagEncoder( int speedBits, double speedFactor )
    {
        super(speedBits, speedFactor);

        hikingNetworkToCode.put("iwn", BEST.getValue());
        hikingNetworkToCode.put("nwn", BEST.getValue());
        hikingNetworkToCode.put("rwn", VERY_NICE.getValue());
        hikingNetworkToCode.put("lwn", VERY_NICE.getValue());

        // MARQ24 - the call of the method init() is missing!! ?!
        Logger.getLogger(HikingFlagEncoder.class.getName()).warn("ORS \"HIKING\" FlagEncoder should not be used anylonger - please use \"HIKE\" instead");
    }

    @Override
    public int getVersion()
    {
        return 2;
    }

    @Override
    public long acceptWay(ReaderWay way )
    {
        String highwayValue = way.getTag("highway");
        if (highwayValue == null)
        {
            if (way.hasTag("route", ferries))
            {
                String footTag = way.getTag("foot");
                if (footTag == null || "yes".equals(footTag))
                    return acceptBit | ferryBit;
            }

            // special case not for all acceptedRailways, only platform
            if (way.hasTag("railway", "platform"))
                return acceptBit;

            return 0;
        }

        // hiking allows all sac_scale values
        // String sacScale = way.getTag("sac_scale");
        if (way.hasTag("sidewalk", sidewalkValues))
            return acceptBit;

        // no need to evaluate ferries or fords - already included here
        if (way.hasTag("foot", intendedValues))
            return acceptBit;

        if (!allowedHighwayTags.contains(highwayValue))
            return 0;

        if (way.hasTag("motorroad", "yes"))
            return 0;

        // do not get our feet wet, "yes" is already included above
        if (isBlockFords() && (way.hasTag("highway", "ford") || way.hasTag("ford")))
            return 0;

        // check access restrictions
        if (way.hasTag(restrictions, restrictedValues) /*&& !getConditionalTagInspector().isRestrictedWayConditionallyPermitted(way)*/) // Runge
            return 0;

      /*  if (getConditionalTagInspector().isPermittedWayConditionallyRestricted(way))
            return 0;
        else*/ // Runge
            return acceptBit;
    }

    @Override
    protected void collect(ReaderWay way, TreeMap<Double, Integer> weightToPrioMap )
    {
        String highway = way.getTag("highway");
        if (way.hasTag("foot", "designated"))
            weightToPrioMap.put(100d, PREFER.getValue());

        double maxSpeed = getMaxSpeed(way);
        if (safeHighwayTags.contains(highway) || maxSpeed > 0 && maxSpeed <= 20)
        {
            weightToPrioMap.put(40d, PREFER.getValue());
            if (way.hasTag("tunnel", intendedValues))
            {
                if (way.hasTag("sidewalk", sidewalksNoValues))
                    weightToPrioMap.put(40d, REACH_DEST.getValue());
                else
                    weightToPrioMap.put(40d, UNCHANGED.getValue()); 
            }
        } else if (maxSpeed > 50 || avoidHighwayTags.contains(highway))
        {
            if (way.hasTag("sidewalk", sidewalksNoValues))
                weightToPrioMap.put(45d, WORST.getValue());
            else
                weightToPrioMap.put(45d, REACH_DEST.getValue());
        }

        if (way.hasTag("bicycle", "official") || way.hasTag("bicycle", "designated"))
            weightToPrioMap.put(44d, AVOID_IF_POSSIBLE.getValue());
    }

    @Override
    public boolean supports( Class<?> feature )
    {
        if (super.supports(feature))
            return true;

        return PriorityWeighting.class.isAssignableFrom(feature);
    }

    @Override
    public String toString()
    {
        return FlagEncoderNames.HIKING;
    }
}