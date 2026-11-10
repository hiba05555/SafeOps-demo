package ma.ensa.apigateway.auth.util;


import org.mapstruct.Mapper;

@Mapper(componentModel = "spring", uses = {JwtAdapter.class})
public interface TokenMapper {

    Jwt toJwt(TokenDto tokenDto);
}
