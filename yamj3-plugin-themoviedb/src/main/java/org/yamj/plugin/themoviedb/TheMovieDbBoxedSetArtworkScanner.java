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

import static org.yamj.plugin.api.service.Constants.SOURCE_TMDB;

import com.omertron.themoviedbapi.enumeration.ArtworkType;
import com.omertron.themoviedbapi.model.artwork.Artwork;
import com.omertron.themoviedbapi.model.collection.Collection;
import com.omertron.themoviedbapi.results.ResultList;
import java.util.Collections;
import java.util.List;
import org.yamj.plugin.api.artwork.ArtworkDTO;
import org.yamj.plugin.api.artwork.BoxedSetArtworkScanner;
import org.yamj.plugin.api.model.IBoxedSet;
import ro.fortsoft.pf4j.Extension;

@Extension
public final class TheMovieDbBoxedSetArtworkScanner extends AbstractTheMovieDbArtworkScanner implements BoxedSetArtworkScanner {

    @Override
    public List<ArtworkDTO> getPosters(IBoxedSet boxedSet) {
        String tmdbId = boxedSet.getId(SOURCE_TMDB);
        int id;
        if (isNoValidTheMovieDbId(tmdbId)) {
            Collection collection = theMovieDbApiWrapper.findCollection(boxedSet.getName(), locale.getLanguage());
            if (collection == null) {
                return Collections.emptyList();
            }
            id = collection.getId();
        } else {
            id = Integer.parseInt(tmdbId);
        }

        ResultList<Artwork> resultList = theMovieDbApiWrapper.getCollectionImages(id);
        return this.filterArtwork(tmdbId, resultList, locale.getLanguage(), ArtworkType.POSTER, DEFAULT_SIZE);
    }

    @Override
    public List<ArtworkDTO> getFanarts(IBoxedSet boxedSet) {
        String tmdbId = boxedSet.getId(SOURCE_TMDB);
        int id;
        if (isNoValidTheMovieDbId(tmdbId)) {
            Collection collection = theMovieDbApiWrapper.findCollection(boxedSet.getName(), locale.getLanguage());
            if (collection == null) {
                return Collections.emptyList();
            }
            id = collection.getId();
        } else {
            id = Integer.parseInt(tmdbId);
        }

        ResultList<Artwork> resultList = theMovieDbApiWrapper.getCollectionImages(id);
        return this.filterArtwork(tmdbId, resultList, locale.getLanguage(), ArtworkType.BACKDROP, DEFAULT_SIZE);
    }

    @Override
    public List<ArtworkDTO> getBanners(IBoxedSet boxedSet) {
        return Collections.emptyList();
    }
}
