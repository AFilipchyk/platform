CREATE OR REPLACE FUNCTION cast_to_custom_file(file bytea, ext VARCHAR) RETURNS bytea AS 
$$
BEGIN
	if ((ext = 'doc' or ext = 'xls') and (length(file) > 3) and (get_byte(file, 0) = 80) and (get_byte(file, 1) = 75) and (get_byte(file, 2) = 3) and (get_byte(file, 3) = 4)) then
		ext = ext || 'x';
	end if;

	RETURN chr(octet_length(ext))::bytea || convert_to(ext, 'UTF-8') || file;
END;
$$ LANGUAGE 'plpgsql' IMMUTABLE;


CREATE OR REPLACE FUNCTION cast_from_custom_file(file bytea) RETURNS bytea AS 
$$
BEGIN
	RETURN substring(file, (get_byte(file, 0) + 2)); -- index in substring is 1-based
END;
$$ LANGUAGE 'plpgsql' IMMUTABLE;

CREATE OR REPLACE FUNCTION cast_file_to_string(file bytea) RETURNS VARCHAR AS 
$$
BEGIN
	RETURN convert_from(file, 'UTF-8');
EXCEPTION
  WHEN OTHERS THEN
    RETURN NULL;
END;
$$ LANGUAGE 'plpgsql' IMMUTABLE;

CREATE OR REPLACE FUNCTION cast_string_to_file(string VARCHAR) RETURNS bytea AS 
$$
BEGIN
	RETURN convert_to(string, 'UTF-8');
END;
$$ LANGUAGE 'plpgsql' IMMUTABLE;

CREATE OR REPLACE FUNCTION get_extension(file bytea) RETURNS VARCHAR AS 
$$
BEGIN
	RETURN convert_from(substring(file, 2, get_byte(file, 0)), 'UTF-8');  -- index in substring is 1-based
END;
$$ LANGUAGE 'plpgsql' IMMUTABLE;