#ifndef TERM_PROVIDERS_HPP
#define TERM_PROVIDERS_HPP

#include <string>
#include <vector>

#include "shard.hpp"
#include "term_iterator.hpp"
#include "term_provider.hpp"

namespace imhotep {

    template <typename term_t>
    class TermProviders : public std::vector<std::pair<std::string, TermProvider<term_t>>> {
    public:
        TermProviders(const std::vector<Shard*>&      shards,
                      const std::vector<std::string>& field_names,
                      const std::string&              split_dir,
                      size_t                          num_splits,
                      ExecutorService&                executor);

    private:
        typedef TermIterator<term_t> term_it;

        typedef typename TermProvider<term_t>::term_source_t term_source_t;

        std::vector<term_source_t> term_sources(const std::vector<Shard*>& shards,
                                                const std::string&         field) const {
            std::vector<term_source_t> result;
            for (std::vector<Shard*>::const_iterator it(shards.begin());
                 it != shards.end(); ++it) {
                Shard* shard(*it);
                const VarIntView& view(shard->term_view<term_t>(field));
                result.emplace_back(std::make_pair(shard, term_it(view)));
            }
            return result;
        }
    };

} // namespace imhotep

#endif
