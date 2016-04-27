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
package org.yamj.plugin.fanarttv;

import static org.yamj.plugin.api.Constants.SOURCE_TVDB;

import com.omertron.fanarttvapi.enumeration.FTArtworkType;
import com.omertron.fanarttvapi.model.FTSeries;
import java.util.List;
import org.apache.commons.lang3.StringUtils;
import org.yamj.plugin.api.artwork.ArtworkDTO;
import org.yamj.plugin.api.artwork.SeriesArtworkScanner;
import org.yamj.plugin.api.metadata.SeriesScanner;
import org.yamj.plugin.api.model.IEpisode;
import org.yamj.plugin.api.model.ISeason;
import org.yamj.plugin.api.model.ISeries;
import ro.fortsoft.pf4j.Extension;


@Extension
public final class FanartTvSeriesArtworkScanner extends AbstractFanartTvArtworkScanner implements SeriesArtworkScanner {
    
    @Override
    public List<ArtworkDTO> getPosters(ISeason season) {
        String tvdbId = this.getTvdbId(season.getSeries());
        return getSeriesArtworkType(tvdbId, FTArtworkType.SEASONPOSTER, season.getNumber());
    }

    @Override
    public List<ArtworkDTO> getPosters(ISeries series) {
        String tvdbId = this.getTvdbId(series);
        return getSeriesArtworkType(tvdbId, FTArtworkType.TVPOSTER, -1);
    }

    @Override
    public List<ArtworkDTO> getFanarts(ISeason season) {
        String tvdbId = this.getTvdbId(season.getSeries());
        return getSeriesArtworkType(tvdbId, FTArtworkType.SHOWBACKGROUND, -1);
    }

    @Override
    public List<ArtworkDTO> getFanarts(ISeries series) {
        String tvdbId = this.getTvdbId(series);
        return getSeriesArtworkType(tvdbId, FTArtworkType.SHOWBACKGROUND, -1);
    }

    @Override
    public List<ArtworkDTO> getBanners(ISeason season) {
        String tvdbId = this.getTvdbId(season.getSeries());
        return getSeriesArtworkType(tvdbId, FTArtworkType.SEASONBANNER, season.getNumber());
    }

    @Override
    public List<ArtworkDTO> getBanners(ISeries series) {
        String tvdbId = this.getTvdbId(series);
        return getSeriesArtworkType(tvdbId, FTArtworkType.TVBANNER, -1);
    }

    @Override
    public List<ArtworkDTO> getVideoImages(IEpisode episode) {
        return null;
    }

    private String getTvdbId(ISeries series) {
        String tvdbId = series.getId(SOURCE_TVDB);
        if (StringUtils.isBlank(tvdbId)) {
            SeriesScanner tvdbScanner = metadataService.getSeriesScanner(SOURCE_TVDB);
            if (tvdbScanner != null) {
                tvdbId = tvdbScanner.getSeriesId(series, false);
            }
        }
        return tvdbId;
    }
    
    /**
     * Generic routine to get the artwork type from the FanartTV based on the passed type.
     *
     * @param id the ID of the movie to get
     * @param artworkType type of the artwork to get
     * @return list of the appropriate artwork
     */
    private List<ArtworkDTO> getSeriesArtworkType(String id, FTArtworkType artworkType, int seasonNumber) {
        if (StringUtils.isBlank(id)) {
            return null;
        }
        
        FTSeries ftSeries = fanartTvApiWrapper.getFanartSeries(id);
        if (ftSeries == null) {
            return null;
        }

        return getArtworkList(ftSeries.getArtwork(artworkType), seasonNumber);
    }
}
