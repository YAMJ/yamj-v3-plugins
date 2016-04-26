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

import static org.yamj.plugin.api.Constants.SOURCE_IMDB;

import com.omertron.fanarttvapi.enumeration.FTArtworkType;
import com.omertron.fanarttvapi.model.FTMovie;
import java.util.List;
import org.apache.commons.lang3.StringUtils;
import org.yamj.plugin.api.artwork.ArtworkDTO;
import org.yamj.plugin.api.artwork.MovieArtworkScanner;
import org.yamj.plugin.api.metadata.MovieScanner;
import org.yamj.plugin.api.model.IMovie;
import ro.fortsoft.pf4j.Extension;

@Extension
public final class FanartTvMovieArtworkScanner extends AbstractFanartTvArtworkScanner implements MovieArtworkScanner {
    
    @Override
    public List<ArtworkDTO> getPosters(IMovie movie) {
        String imdbId = getImdbId(movie);
        return getMovieArtworkType(imdbId, FTArtworkType.MOVIEPOSTER);
}

    @Override
    public List<ArtworkDTO> getFanarts(IMovie movie) {
        String imdbId = getImdbId(movie);
        return getMovieArtworkType(imdbId, FTArtworkType.MOVIEBACKGROUND);
    }

    private String getImdbId(IMovie movie) {
        String imdbId = movie.getId(SOURCE_IMDB);
        if (StringUtils.isBlank(imdbId)) {
            MovieScanner imdbScanner = metadataService.getMovieScanner(SOURCE_IMDB);
            if (imdbScanner != null) {
                imdbId = imdbScanner.getMovieId(movie, false);
            }
        }
        return imdbId;
    }
    
    /**
     * Generic routine to get the artwork type from the FanartTV based on the passed type.
     *
     * @param id the ID of the movie to get
     * @param artworkType type of the artwork to get
     * @return list of the appropriate artwork
     */
    private List<ArtworkDTO> getMovieArtworkType(String id, FTArtworkType artworkType) {
        if (StringUtils.isBlank(id)) {
            return null;
        }
        
        FTMovie ftMovie = fanartTvApiWrapper.getFanartMovie(id);
        if (ftMovie == null) {
            return null;
        }
        
        return getArtworkList(ftMovie.getArtwork(artworkType), locale.getLanguage(), -1);
    }

}
