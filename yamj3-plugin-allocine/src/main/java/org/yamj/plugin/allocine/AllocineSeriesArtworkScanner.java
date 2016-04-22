/*
 *      Copyright (c) 2004-2016 YAMJ Members
 *      https://github.com/organizations/YAMJ/teams
 *
 *      This file is part of the Yet Another Media Jukebox (YAMJ) plugins.
 *
 *      YAMJ is free software: you can redistribute it and/or modify
 *      it under the terms of the GNU General Public License as published by
 *      the Free Software Foundation, either version 3 of the License, or
 *      any later version.
 *
 *      YAMJ is distributed in the hope that it will be useful,
 *      but WITHOUT ANY WARRANTY; without even the implied warranty of
 *      MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *      GNU General Public License for more details.
 *
 *      You should have received a copy of the GNU General Public License
 *      along with YAMJ.  If not, see <http://www.gnu.org/licenses/>.
 *
 *      Web: https://github.com/YAMJ/yamj-v3-plugins
 *
 */
package org.yamj.plugin.allocine;

import static org.yamj.plugin.allocine.AllocinePlugin.SCANNER_NAME;

import com.moviejukebox.allocine.model.TvSeasonInfos;
import com.moviejukebox.allocine.model.TvSeriesInfos;
import java.util.Collections;
import java.util.List;
import org.apache.commons.collections.MapUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yamj.plugin.api.artwork.ArtworkDTO;
import org.yamj.plugin.api.artwork.SeriesArtworkScanner;
import org.yamj.plugin.api.metadata.EpisodeDTO;
import org.yamj.plugin.api.metadata.SeasonDTO;
import org.yamj.plugin.api.metadata.SeriesDTO;
import ro.fortsoft.pf4j.Extension;

@Extension
public final class AllocineSeriesArtworkScanner extends AbstractAllocineScanner implements SeriesArtworkScanner {

    private static final Logger LOG = LoggerFactory.getLogger(AllocineSeriesArtworkScanner.class);

    @Override
    public List<ArtworkDTO> getPosters(SeasonDTO season) {
        String allocineId = season.getIds().get(SCANNER_NAME);
        if (isNoValidAllocineId(allocineId)) {
            LOG.debug("Allocine id not available '{}' - Season {}", season.getSeries().getTitle(), season.getSeasonNumber());
            return Collections.emptyList();
        }

        TvSeasonInfos tvSeasonInfos = allocineApiWrapper.getTvSeasonInfos(allocineId);
        if (tvSeasonInfos == null || tvSeasonInfos.isNotValid() || MapUtils.isEmpty(tvSeasonInfos.getPosters())) {
            return Collections.emptyList();
        }
        return buildArtworkDetails(tvSeasonInfos.getPosters());
    }

    @Override
    public List<ArtworkDTO> getPosters(SeriesDTO series) {
        String allocineId = getSeriesId(series.getTitle(), series.getOriginalTitle(), series.getStartYear(), series.getIds(), false);
        if (isNoValidAllocineId(allocineId)) {
            LOG.debug("Allocine id not available '{}'", series.getTitle());
            return Collections.emptyList();
        }
        
        TvSeriesInfos tvSeriesInfos = allocineApiWrapper.getTvSeriesInfos(allocineId, false);
        if (tvSeriesInfos == null || tvSeriesInfos.isNotValid() || MapUtils.isEmpty(tvSeriesInfos.getPosters())) {
            return Collections.emptyList();
        }
        return buildArtworkDetails(tvSeriesInfos.getPosters());
    }

    @Override
    public List<ArtworkDTO> getFanarts(SeasonDTO season) {
        return Collections.emptyList();
    }

    @Override
    public List<ArtworkDTO> getFanarts(SeriesDTO series) {
        return Collections.emptyList();
    }

    @Override
    public List<ArtworkDTO> getBanners(SeasonDTO season) {
        return Collections.emptyList();
    }

    @Override
    public List<ArtworkDTO> getBanners(SeriesDTO series) {
        return Collections.emptyList();
    }

    @Override
    public List<ArtworkDTO> getVideoImages(EpisodeDTO episode) {
        return Collections.emptyList();
    }
}