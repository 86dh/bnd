package aQute.bnd.build.model.conversions;

import aQute.bnd.build.model.clauses.*;
import aQute.bnd.header.*;
import aQute.libg.tuple.*;

public class VersionedClauseConverter implements Converter<VersionedClause,Pair<String,Attrs>> {
	public VersionedClause convert(Pair<String,Attrs> input) throws IllegalArgumentException {
		if (input == null)
			return null;
		return new VersionedClause(input.getFirst(), input.getSecond());
	}

	@Override
	public VersionedClause error(String msg) {
		return VersionedClause.error(msg);
	}
}