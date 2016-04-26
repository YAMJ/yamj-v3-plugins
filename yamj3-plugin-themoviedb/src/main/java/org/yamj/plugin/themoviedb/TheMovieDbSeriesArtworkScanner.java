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
package org.yamj.plugin.themoviedb;

import com.omertron.themoviedbapi.enumeration.ArtworkType;
import com.omertron.themoviedbapi.model.artwork.Artwork;
import com.omertron.themoviedbapi.results.ResultList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yamj.plugin.api.artwork.ArtworkDTO;
import org.yamj.plugin.api.artwork.SeriesArtworkScanner;
import org.yamj.plugin.api.model.IEpisode;
import org.yamj.plugin.api.model.ISeason;
import org.yamj.plugin.api.model.ISeries;
import ro.fortsoft.pf4j.Extension;

@Extension
public final class TheMovieDbSeriesArtworkScanner extends AbstractTheMovieDbArtworkScanner implements SeriesArtworkScanner {

    private static final Logger LOG = LoggerFactory.getLogger(TheMovieDbSeriesArtworkScanner.class);

    @Override
    public List<ArtworkDTO> getPosters(ISeason season) {
        String tmdbId = getSeriesId(season.getSeries(), false);
        if (isNoValidTheMovieDbId(tmdbId)) {
            LOG.debug("TheMovieDb id not available '{}'", season.getSeries().getTitle());
            return null;
        }
        
        ResultList<Artwork> resultList = theMovieDbApiWrapper.getSeasonImages(Integer.parseInt(tmdbId), season.getNumber());
        return this.filterArtwork(tmdbId, resultList, locale.getLanguage(), ArtworkType.POSTER, DEFAULT_SIZE);
    }

    @Override
    public List<ArtworkDTO> getPosters(ISeries series) {
        String tmdbId = getSeriesId(series, false);
        if (isNoValidTheMovieDbId(tmdbId)) {
            LOG.debug("TheMovieDb id not available '{}'", series.getTitle());
            return null;
        }

        ResultList<Artwork> resultList = theMovieDbApiWrapper.getSeriesImages(Integer.parseInt(tmdbId));
        return this.filterArtwork(tmdbId, resultList, locale.getLanguage(), ArtworkType.POSTER, DEFAULT_SIZE);
    }

    @Override
    public List<ArtworkDTO> getFanarts(ISeason season) {
        String tmdbId = getSeriesId(season.getSeries(), false);
        if (isNoValidTheMovieDbId(tmdbId)) {
            LOG.debug("TheMovieDb id not available '{}'", season.getSeries().getTitle());
            return null;
        }

        ResultList<Artwork> resultList = theMovieDbApiWrapper.getSeasonImages(Integer.parseInt(tmdbId), season.getNumber());
        return this.filterArtwork(tmdbId, resultList, locale.getLanguage(), ArtworkType.BACKDROP, DEFAULT_SIZE);
    }

    @Override
    public List<ArtworkDTO> getFanarts(ISeries series) {
        String tmdbId = getSeriesId(series, false);
        if (isNoValidTheMovieDbId(tmdbId)) {
            LOG.debug("TheMovieDb id not available '{}'", series.getTitle());
            return null;
        }
        
        ResultList<Artwork> resultList = theMovieDbApiWrapper.getSeriesImages(Integer.parseInt(tmdbId));
        return this.filterArtwork(tmdbId, resultList, locale.getLanguage(), ArtworkType.BACKDROP, DEFAULT_SIZE);
    }

    @Override
    public List<ArtworkDTO> getBanners(ISeason season) {
        return null;
    }

    @Override
    public List<ArtworkDTO> getBanners(ISeries series) {
        return null;
    }

    @Override
    public List<ArtworkDTO> getVideoImages(IEpisode episode) {
        String tmdbId = getSeriesId(episode.getSeason().getSeries(), false);
        if (isNoValidTheMovieDbId(tmdbId)) {
            LOG.debug("TheMovieDb id not available '{}'", episode.getSeason().getSeries().getTitle());
            return null;
        }

        ResultList<Artwork> resultList = theMovieDbApiWrapper.getEpisodeImages(Integer.parseInt(tmdbId), episode.getSeason().getNumber(), episode.getNumber());
        return this.filterArtwork(tmdbId, resultList, locale.getLanguage(), ArtworkType.STILL, DEFAULT_SIZE);
    }
}
