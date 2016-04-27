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
import java.util.Locale;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yamj.plugin.api.artwork.ArtworkDTO;
import org.yamj.plugin.api.artwork.MovieArtworkScanner;
import org.yamj.plugin.api.model.IMovie;
import ro.fortsoft.pf4j.Extension;

@Extension
public final class TheMovieDbMovieArtworkScanner extends AbstractTheMovieDbArtworkScanner implements MovieArtworkScanner {

    private static final Logger LOG = LoggerFactory.getLogger(TheMovieDbMovieArtworkScanner.class);

    @Override
    public List<ArtworkDTO> getPosters(IMovie movie) {
        String tmdbId = getMovieId(movie, false);
        if (isNoValidTheMovieDbId(tmdbId)) {
            LOG.debug("TheMovieDb id not available '{}'", movie.getTitle());
            return null;
        }

        final Locale locale = localeService.getLocale();
        ResultList<Artwork> resultList = theMovieDbApiWrapper.getMovieImages(Integer.parseInt(tmdbId));
        return this.filterArtwork(tmdbId, resultList, locale.getLanguage(), ArtworkType.POSTER, DEFAULT_SIZE);
    }

    @Override
    public List<ArtworkDTO> getFanarts(IMovie movie) {
        String tmdbId = getMovieId(movie, false);
        if (isNoValidTheMovieDbId(tmdbId)) {
            LOG.debug("TheMovieDb id not available '{}'", movie.getTitle());
            return null;
        }
        
        final Locale locale = localeService.getLocale();
        ResultList<Artwork> resultList = theMovieDbApiWrapper.getMovieImages(Integer.parseInt(tmdbId));
        return this.filterArtwork(tmdbId, resultList, locale.getLanguage(), ArtworkType.BACKDROP, DEFAULT_SIZE);
    }
}
